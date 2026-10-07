package org.acme.reservation.adapter.out.inventory;

import com.sun.net.httpserver.HttpServer;
import io.smallrye.graphql.client.InvalidResponseException;
import io.smallrye.graphql.client.vertx.typesafe.VertxTypesafeGraphQLClientBuilder;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.ServerSocket;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caracteriza como o cliente GraphQL typesafe real classifica cada falha, porque a politica de
 * retry e fallback de {@link GraphQLInventoryGateway} so funciona se ela mirar os tipos que o
 * cliente produz de fato.
 *
 * <p>Nao e teste do gateway: nao injeta mock nem sobe o Quarkus. E o contrato com a biblioteca,
 * medido contra um servidor HTTP de verdade.
 *
 * <p>A falha e observada <b>por assinatura</b>, nunca por {@code await()}: o
 * {@code await().indefinitely()} re-empacota excecao checada em
 * {@code CompletionException}, e o Mutiny remove esse embrulho antes de emitir. Medir por
 * {@code await()} atribuiria ao cliente uma forma de falha que e do proprio teste.
 */
class GraphQLInventoryClientFailureTest {

    @Timeout(30)
    @Test
    void shouldEmitIoExceptionWhenTheConnectionIsRefused() throws Exception {
        int port = freePort();
        Vertx vertx = Vertx.vertx();
        try {
            Throwable emitted = emittedFailure(vertx, port);

            assertInstanceOf(IOException.class, emitted,
                    "o cliente emite a causa crua: o Mutiny desembrulha o CompletionStage");
        } finally {
            vertx.close();
        }
    }

    @Timeout(30)
    @Test
    void shouldRejectAnHttpFailureThatHasNoGraphqlEnvelope() throws Exception {
        HttpServer server = serverThatAnswers(503, "text/html", "<html>gateway blew up</html>");
        Vertx vertx = Vertx.vertx();
        try {
            Throwable emitted = emittedFailure(vertx, server.getAddress().getPort());

            assertInstanceOf(InvalidResponseException.class, emitted);
            assertTrue(emitted.getMessage().contains("503"), emitted.getMessage());
        } finally {
            vertx.close();
            server.stop(0);
        }
    }

    @Timeout(30)
    @Test
    void shouldReportGraphqlErrorsAsClientException() throws Exception {
        HttpServer server = serverThatAnswers(200, "application/json",
                "{\"errors\":[{\"message\":\"schema exploded\"}]}");
        Vertx vertx = Vertx.vertx();
        try {
            Throwable emitted = emittedFailure(vertx, server.getAddress().getPort());

            assertInstanceOf(io.smallrye.graphql.client.GraphQLClientException.class, emitted);
        } finally {
            vertx.close();
            server.stop(0);
        }
    }

    private static Throwable emittedFailure(Vertx vertx, int port) throws InterruptedException {
        var client = new VertxTypesafeGraphQLClientBuilder()
                .vertx(vertx)
                .configKey("failure-characterization")
                .endpoint(URI.create("http://127.0.0.1:" + port + "/graphql"))
                .build(GraphQLInventoryClient.class);

        AtomicReference<Throwable> emitted = new AtomicReference<>();
        CountDownLatch finished = new CountDownLatch(1);
        client.allCars().subscribe().with(
                cars -> {
                    emitted.set(new AssertionError("o cliente deveria ter falhado, respondeu: " + cars));
                    finished.countDown();
                },
                failure -> {
                    emitted.set(failure);
                    finished.countDown();
                });

        assertTrue(finished.await(15, TimeUnit.SECONDS), "a falha nao chegou em 15s");
        Throwable failure = emitted.get();
        assertNotNull(failure, "nada foi emitido");
        return failure;
    }

    /**
     * Porta que ninguem responde: o SO recusa a conexao. Existe uma corrida teorica (a porta
     * pode ser tomada entre fechar e conectar), mas qualquer falha de conexao continua sendo
     * {@link IOException}, que e o que este teste quer observar.
     */
    private static int freePort() throws IOException {
        try (ServerSocket socket = new ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static HttpServer serverThatAnswers(int status, String contentType, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        return server;
    }
}
