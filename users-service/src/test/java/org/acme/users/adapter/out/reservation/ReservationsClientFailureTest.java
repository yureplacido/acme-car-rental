package org.acme.users.adapter.out.reservation;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.eclipse.microprofile.rest.client.RestClientBuilder;
import org.jboss.resteasy.reactive.RestQuery;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Caracteriza como o cliente REST real do {@link AvailabilityClient} classifica cada falha,
 * porque o tratamento de {@link org.acme.users.application.exception.AvailabilityUnavailable}
 * so funciona se o adapter de saida mirar os tipos que o cliente produz de fato.
 *
 * <p>Nao da para exercitar o proprio {@link AvailabilityClient}: ele leva {@code @AccessToken} e
 * o filtro de propagacao esta ligado a interface (nao a injecao), abortando com 401 antes de a
 * requisicao sair do app - medido nas tres falhas abaixo, todas devolvendo 401. Por isso a
 * medicao usa uma interface-sonda equivalente em {@code src/test} (mesmo path/metodo), sem
 * {@code @AccessToken}: e o mesmo cliente resteasy-reactive, medindo o mapeamento de erro
 * recebido (503) e de falha de transporte, que nao depende do filtro de token.
 *
 * <p>{@code @QuarkusTest} aqui e <b>obrigatorio, nao conveniencia</b>. O {@code RestClientBuilder}
 * do MP REST Client resolve seu builder via {@code RestClientBuilderResolver}, e o
 * {@code resteasy-reactive-client} nao registra nenhum implementor fora do app em execucao (sem
 * {@code META-INF/services/org.eclipse.microprofile.rest.client.spi.RestClientBuilderResolver} e
 * sem {@code static} que o instale). Sem boot do Quarkus a sonda falha com
 * {@code IllegalStateException: No RestClientBuilderResolver implementation found!} - verificado
 * removendo a anotacao e rodando a classe. O par no reservation-service
 * ({@code GraphQLInventoryClientFailureTest}) e JUnit puro porque mede outro cliente, o
 * SmallRye GraphQL, que sobe sem boot. Ver {@code docs/testing.md}.
 */
@QuarkusTest
@Timeout(30)
class ReservationsClientFailureTest {

    private static final LocalDate START = LocalDate.of(2035, 6, 1);
    private static final LocalDate END = LocalDate.of(2035, 6, 10);

    @Path("reservations")
    interface AvailabilityProbe {
        @GET
        @Path("availability")
        String availability(@RestQuery LocalDate startDate, @RestQuery LocalDate endDate);
    }

    @Test
    void shouldCharacterizeTheFailureWhenReservationsAnswers503() throws IOException {
        Throwable thrown = callAgainstServer(exchange -> {
            byte[] body = "{\"code\":\"INVENTORY_UNAVAILABLE\"}".getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(503, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertNotNull(thrown);
        assertInstanceOf(WebApplicationException.class, thrown,
                "resposta nao-2xx chega como WebApplicationException (medido: "
                        + "ClientWebApplicationException), e e ela que o catch do gateway mira");
        assertEquals(503, ((WebApplicationException) thrown).getResponse().getStatus(),
                "o status do outro lado e preservado");
        System.out.println("MEASURED-503: " + describe(thrown));
    }

    @Test
    void shouldCharacterizeTheFailureWhenTheConnectionIsRefused() throws IOException {
        Throwable thrown = callAgainstClosedPort();

        assertNotNull(thrown);
        assertInstanceOf(ProcessingException.class, thrown,
                "conexao recusada chega embrulhada em ProcessingException, e e o que o catch mira");
        System.out.println("MEASURED-CONNECTION-REFUSED: " + describe(thrown));
    }

    @Test
    void shouldCharacterizeTheFailureWhenReservationsNeverResponds() throws IOException {
        Throwable thrown = callAgainstServer(exchange -> {
            try {
                Thread.sleep(3_000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            byte[] body = "[]".getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });

        assertNotNull(thrown);
        assertInstanceOf(ProcessingException.class, thrown,
                "estouro de prazo tambem chega como ProcessingException, com o mesmo embrulho da "
                        + "conexao recusada - e e por isso que as duas compartilham o mesmo catch");
        assertInstanceOf(TimeoutException.class, thrown.getCause(),
                "a causa identifica o estouro de prazo, para o diagnostico nao confundir com rede");
        System.out.println("MEASURED-READ-TIMEOUT: " + describe(thrown));
    }

    private Throwable callAgainstServer(ExchangeHandler handler) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/reservations/availability", exchange -> {
            try {
                handler.handle(exchange);
            } catch (Exception e) {
                exchange.close();
            }
        });
        server.start();
        try {
            return invoke(URI.create("http://127.0.0.1:" + server.getAddress().getPort()));
        } finally {
            server.stop(0);
        }
    }

    private Throwable callAgainstClosedPort() throws IOException {
        int freePort;
        try (ServerSocket socket = new ServerSocket(0)) {
            freePort = socket.getLocalPort();
        }
        return invoke(URI.create("http://127.0.0.1:" + freePort));
    }

    private Throwable invoke(URI baseUri) {
        AvailabilityProbe client = RestClientBuilder.newBuilder()
                .baseUri(baseUri)
                .connectTimeout(300, TimeUnit.MILLISECONDS)
                .readTimeout(500, TimeUnit.MILLISECONDS)
                .build(AvailabilityProbe.class);
        AtomicReference<Throwable> thrown = new AtomicReference<>();
        try {
            client.availability(START, END);
        } catch (Throwable t) {
            thrown.set(t);
        }
        return thrown.get();
    }

    private String describe(Throwable thrown) {
        StringBuilder description = new StringBuilder();
        description.append("class=").append(thrown.getClass().getName());
        description.append(" | isWebApplicationException=").append(thrown instanceof WebApplicationException);
        description.append(" | isProcessingException=").append(thrown instanceof ProcessingException);
        description.append(" | message=").append(thrown.getMessage());
        if (thrown instanceof WebApplicationException wae) {
            description.append(" | status=").append(wae.getResponse().getStatus());
        }
        Throwable cause = thrown.getCause();
        if (cause != null) {
            description.append(" | causeClass=").append(cause.getClass().getName());
            description.append(" | causeMessage=").append(cause.getMessage());
        }
        return description.toString();
    }

    @FunctionalInterface
    private interface ExchangeHandler {
        void handle(HttpExchange exchange) throws Exception;
    }
}
