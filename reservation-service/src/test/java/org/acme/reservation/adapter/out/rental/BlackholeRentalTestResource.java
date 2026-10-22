package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Sobe um Consul real e publica nele a instancia "rentals" que o teste usa — mas, ao contrario
 * do stub de {@link ConsulRentalDiscoveryTestResource}, a instancia e um <b>buraco negro</b>:
 * aceita a conexao, le o pedido e NUNCA responde.
 *
 * <p>Este e o outro lado que a ADR 009 §4 exige: nao e um cliente mockado (que nao tem socket
 * para abortar), e um peer TCP de verdade — a conexao existe, o pedido sai pelo socket e o
 * fechamento e observado pela outra ponta dele. O peer roda na JVM do teste (o que a prova
 * exige nao e isolamento de processo do peer, e sim que o socket do cliente seja mesmo fechado
 * e que o outro ponta veja).
 *
 * <p>O buraco negro e um {@link ServerSocket} cru, e nao um {@code HttpServer}, por uma razao
 * medida: o abort do cliente chega como <b>fim de stream</b>, e nao como reset com RST — o
 * fechamento e limpo. O EOF na proxima leitura e o sinal que distingue "o cliente abortou" de
 * "o cliente desistiu por timeout, mas deixou a conexao pendurada" — que e exatamente a
 * distincao que a ADR precisa. E o que este harness mede: o EOF, e nao a escrita posterior no
 * socket (que ele nao exercita).
 *
 * <p>Consul e necessario (e nao um {@code http://} direto) porque o {@code RentalClient} de
 * producao aponta para {@code stork://rentals}: o caminho real do teste passa pelo mesmo
 * registro, a mesma resolucao e o mesmo cliente de producao.
 *
 * <p><b>Execucao sequencial.</b> A fila de conexoes e estatica, e o recurso de teste e a classe
 * de teste rodam na mesma JVM — e isso pressupoe que as classes nao rodem em paralelo: se duas
 * rodassem juntas, uma consumiria a conexao da outra. A dependencia esta declarada aqui para
 * quem for habilitar execucao paralela no repo.
 */
public class BlackholeRentalTestResource implements QuarkusTestResourceLifecycleManager {

    /**
     * Uma conexao aceita pelo buraco negro, com o que ela observou do cliente.
     *
     * <p>O estado e estatico porque o recurso de teste e a classe de teste rodam na mesma JVM:
     * e a unica forma de o teste ver a observacao do outro lado do socket.
     */
    public static final class BlackholeConnection {

        private final CountDownLatch requestReceived = new CountDownLatch(1);
        private final CountDownLatch clientClosed = new CountDownLatch(1);
        private final AtomicReference<String> outcome = new AtomicReference<>("sem observacao");
        private final AtomicReference<String> requestHead = new AtomicReference<>("");
        private final AtomicBoolean closedCleanly = new AtomicBoolean();
        private volatile Socket socket;

        boolean awaitRequest(long timeout, TimeUnit unit) throws InterruptedException {
            return requestReceived.await(timeout, unit);
        }

        boolean awaitClientClose(long timeout, TimeUnit unit) throws InterruptedException {
            return clientClosed.await(timeout, unit);
        }

        String outcome() {
            return outcome.get();
        }

        /** O head HTTP que o cliente enviou, para provar que o pedido saiu de verdade. */
        String requestHead() {
            return requestHead.get();
        }

        /**
         * O cliente fechou a conexao com fim de stream (fechamento limpo), e nao com reset.
         *
         * <p>E o discriminador que a ADR 009 §4 afirma: um reset (broken pipe) indicaria outro
         * mecanismo, e a distincao entre "abortou" e "desistiu" continua valendo nos dois casos
         * — o que muda e <i>como</i> a conexao morreu.
         */
        boolean closedCleanly() {
            return closedCleanly.get();
        }

        void close() {
            Socket open = socket;
            if (open != null) {
                try {
                    open.close();
                } catch (IOException ignored) {
                    // o socket ja pode estar fechado: nao ha o que fazer
                }
            }
        }
    }

    /**
     * As conexoes aceitas, em ordem de chegada.
     *
     * <p>E uma fila, e nao uma lista indexada: cada teste consome exatamente uma conexao (a
     * sua), e a fila entrega a mais antiga sem a condicao de corrida de "contar antes e
     * conferir depois".
     */
    static final BlockingQueue<BlackholeConnection> CONNECTIONS = new LinkedBlockingQueue<>();

    private GenericContainer<?> consul;
    private ServerSocket blackhole;

    @Override
    public Map<String, String> start() {
        // O estado e estatico e sobrevive ao restart da aplicacao: o que sobrou de uma
        // instancia anterior nao pode vazar para esta.
        resetConnections();

        consul = new GenericContainer<>("hashicorp/consul:1.20").withExposedPorts(8500);
        consul.start();

        try {
            blackhole = startBlackhole();
        } catch (IOException e) {
            throw new IllegalStateException("buraco negro do teste nao subiu", e);
        }
        registerInConsul(blackhole.getLocalPort());
        awaitDiscoverable();

        return Map.of(
                "quarkus.stork.rentals.service-discovery.consul-host", consul.getHost(),
                "quarkus.stork.rentals.service-discovery.consul-port",
                String.valueOf(consul.getMappedPort(8500)));
    }

    @Override
    public void stop() {
        if (blackhole != null) {
            try {
                blackhole.close();
            } catch (IOException ignored) {
                // o socket ja pode estar fechado
            }
        }
        resetConnections();
        if (consul != null) {
            consul.stop();
        }
    }

    /** Fecha as conexoes pendentes e esvazia a fila. */
    private void resetConnections() {
        CONNECTIONS.forEach(BlackholeConnection::close);
        CONNECTIONS.clear();
    }

    private ServerSocket startBlackhole() throws IOException {
        ServerSocket socket = new ServerSocket(0, 16, InetAddress.getByName("127.0.0.1"));
        Thread acceptor = new Thread(() -> {
            while (!socket.isClosed()) {
                try {
                    handle(socket.accept());
                } catch (IOException e) {
                    return; // socket fechado: fim do teste
                }
            }
        }, "blackhole-acceptor");
        acceptor.setDaemon(true);
        acceptor.start();
        return socket;
    }

    private void handle(Socket socket) {
        BlackholeConnection connection = new BlackholeConnection();
        connection.socket = socket;
        CONNECTIONS.add(connection);

        Thread worker = new Thread(() -> {
            try (Socket open = socket) {
                // A janela de observacao e menor que o @Timeout da classe: se o cliente nao
                // fechar, o harness desiste antes — e "desistir" nao pode ser lido como
                // "o cliente fechou".
                open.setSoTimeout((int) TimeUnit.SECONDS.toMillis(10));
                InputStream in = open.getInputStream();
                StringBuilder head = new StringBuilder();
                int read;
                while ((read = in.read()) != -1) {
                    head.append((char) read);
                    if (head.length() > 4 && head.substring(head.length() - 4).equals("\r\n\r\n")) {
                        break;
                    }
                }
                connection.requestHead.set(head.toString());
                connection.requestReceived.countDown();
                connection.outcome.set("pedido recebido (" + head.length()
                        + " bytes) e o cliente continua conectado: nenhuma resposta foi enviada");

                int next = in.read();
                if (next == -1) {
                    connection.closedCleanly.set(true);
                    connection.outcome.set("o cliente FECHOU a conexao (fim de stream lido pelo servidor)");
                } else {
                    connection.outcome.set("o cliente continua enviando apos o pedido (byte " + next + ")");
                }
                connection.clientClosed.countDown();
            } catch (SocketTimeoutException e) {
                // Inatividade do harness, nao fechamento do cliente: nao conta como fechamento.
                connection.outcome.set("observacao encerrada por inatividade do harness "
                        + "(nao e fechamento do cliente): " + e);
            } catch (IOException e) {
                connection.outcome.set("a conexao foi encerrada pelo cliente: " + e);
                connection.clientClosed.countDown();
            }
        }, "blackhole-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void registerInConsul(int port) {
        String registration = """
                {"Name":"rentals","ID":"rentals-blackhole",
                 "Address":"127.0.0.1","Port":%d}""".formatted(port);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + consul.getHost() + ":" + consul.getMappedPort(8500)
                        + "/v1/agent/service/register"))
                .header("Content-Type", "application/json")
                .PUT(HttpRequest.BodyPublishers.ofString(registration))
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() / 100 != 2) {
                throw new IllegalStateException("registro no Consul falhou: HTTP " + response.statusCode());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("nao foi possivel registrar no Consul", e);
        } catch (IOException e) {
            throw new IllegalStateException("nao foi possivel registrar no Consul", e);
        }
    }

    /**
     * Confere que a instancia recem-registrada aparece no catalogo do Consul.
     *
     * <p>Sem esta checagem, uma falha de registro so apareceria no teste como um tempo esgotado
     * sem explicacao — o pior diagnostico possivel para quem tenta entender por que o pedido nao
     * chegou ao buraco negro.
     */
    private void awaitDiscoverable() {
        String catalog = "http://" + consul.getHost() + ":" + consul.getMappedPort(8500)
                + "/v1/health/service/rentals";
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
        while (System.nanoTime() < deadline) {
            try {
                HttpResponse<String> response = HttpClient.newHttpClient()
                        .send(HttpRequest.newBuilder().uri(URI.create(catalog)).GET().build(),
                                HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() / 100 == 2 && response.body().contains("rentals-blackhole")) {
                    return;
                }
            } catch (IOException e) {
                // o Consul ainda nao responde: tenta de novo
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new IllegalStateException("a instancia \"rentals\" nao apareceu no catalogo do Consul");
    }
}
