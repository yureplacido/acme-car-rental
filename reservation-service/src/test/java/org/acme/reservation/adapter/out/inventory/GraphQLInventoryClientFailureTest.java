package org.acme.reservation.adapter.out.inventory;

import com.sun.net.httpserver.HttpServer;
import io.smallrye.graphql.client.vertx.typesafe.VertxTypesafeGraphQLClientBuilder;
import io.vertx.core.Vertx;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletionException;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Caracteriza como o cliente GraphQL typesafe real classifica cada falha, porque a politica de
 * retry e fallback de {@link GraphQLInventoryGateway} so funciona se ela mirar os tipos que o
 * cliente produz de fato.
 *
 * <p>Nao e teste do gateway: nao injeta mock nem sobe o Quarkus. E o contrato com a biblioteca,
 * medido contra um servidor HTTP de verdade.
 */
class GraphQLInventoryClientFailureTest {

    @Test
    void shouldWrapConnectionRefusedInCompletionException() throws Exception {
        int port = availablePort();
        Throwable failure = failureFrom(port);

        assertInstanceOf(CompletionException.class, failure);
        assertInstanceOf(IOException.class, rootCause(failure));
    }

    @Test
    void shouldRejectAnHttpFailureThatHasNoGraphqlEnvelope() throws Exception {
        int port = serverThatAnswers(503, "text/html", "<html>gateway blew up</html>");

        Throwable failure = failureFrom(port);

        assertInstanceOf(io.smallrye.graphql.client.InvalidResponseException.class, rootCause(failure));
        assertTrue(rootCause(failure).getMessage().contains("503"));
    }

    @Test
    void shouldReportGraphqlErrorsAsClientException() throws Exception {
        int port = serverThatAnswers(200, "application/json", "{\"errors\":[{\"message\":\"schema exploded\"}]}");

        Throwable failure = failureFrom(port);

        assertInstanceOf(io.smallrye.graphql.client.GraphQLClientException.class, rootCause(failure));
    }

    private static Throwable failureFrom(int port) {
        Vertx vertx = Vertx.vertx();
        try {
            var client = new VertxTypesafeGraphQLClientBuilder()
                    .vertx(vertx)
                    .configKey("failure-characterization")
                    .endpoint(URI.create("http://127.0.0.1:" + port + "/graphql"))
                    .build(GraphQLInventoryClient.class);
            client.allCars().await().indefinitely();
            throw new AssertionError("o cliente deveria ter falhado");
        } catch (AssertionError | RuntimeException failure) {
            return failure;
        } finally {
            vertx.close();
        }
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }

    private static int availablePort() throws IOException {
        try (java.net.ServerSocket socket = new java.net.ServerSocket(0)) {
            return socket.getLocalPort();
        }
    }

    private static int serverThatAnswers(int status, String contentType, String body) throws IOException {
        HttpServer server = HttpServer.create(new InetSocketAddress(0), 0);
        server.createContext("/graphql", exchange -> {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", contentType);
            exchange.sendResponseHeaders(status, bytes.length);
            exchange.getResponseBody().write(bytes);
            exchange.close();
        });
        server.start();
        Runtime.getRuntime().addShutdownHook(new Thread(() -> server.stop(0)));
        return server.getAddress().getPort();
    }
}
