package org.acme.users.adapter.out.reservation;

import com.sun.net.httpserver.HttpServer;
import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Sobe um Consul real e publica nele a instancia "reservations" que o teste usa.
 *
 * A instancia registrada e um stub no proprio JVM do teste: o Stork roda aqui dentro,
 * entao o endereco precisa ser alcancavel pela JVM (127.0.0.1), nao pelo container do
 * Consul - por isso nao se configura health check HTTP.
 */
public class ConsulTestResource implements QuarkusTestResourceLifecycleManager {

    static final String RESERVATIONS_STUB_JSON =
            "[{\"id\":1,\"userId\":\"alice\",\"carId\":7,"
                    + "\"startDay\":\"2030-01-01\",\"endDay\":\"2030-01-03\","
                    + "\"status\":\"CONFIRMED\"}]";

    private GenericContainer<?> consul;
    private HttpServer stub;

    @Override
    public Map<String, String> start() {
        consul = new GenericContainer<>("hashicorp/consul:1.20").withExposedPorts(8500);
        consul.start();

        stub = startStub();
        registerInConsul(stub.getAddress().getPort());

        return Map.of(
                "quarkus.stork.reservations.service-discovery.consul-host", consul.getHost(),
                "quarkus.stork.reservations.service-discovery.consul-port",
                String.valueOf(consul.getMappedPort(8500)),
                "acme.test.reservations.stub-port",
                String.valueOf(stub.getAddress().getPort()));
    }

    @Override
    public void stop() {
        if (stub != null) {
            stub.stop(0);
        }
        if (consul != null) {
            consul.stop();
        }
    }

    private HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = RESERVATIONS_STUB_JSON.getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException("stub HTTP do teste nao subiu", e);
        }
    }

    private void registerInConsul(int stubPort) {
        String registration = """
                {"Name":"reservations","ID":"reservations-test",
                 "Address":"127.0.0.1","Port":%d}""".formatted(stubPort);
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
        assertDiscoverable();
    }

    private void assertDiscoverable() {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://" + consul.getHost() + ":" + consul.getMappedPort(8500)
                        + "/v1/health/service/reservations?passing=true"))
                .GET()
                .build();
        try {
            HttpResponse<String> response = HttpClient.newHttpClient()
                    .send(request, HttpResponse.BodyHandlers.ofString());
            if (!response.body().contains("\"Port\"")) {
                throw new IllegalStateException("Consul nao devolveu instancia passando: " + response.body());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException(e);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
