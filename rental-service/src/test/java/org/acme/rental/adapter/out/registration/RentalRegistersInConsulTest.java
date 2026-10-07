package org.acme.rental.adapter.out.registration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cap.10 item 9 - service discovery: o rental-service se publica no Consul como "rentals"
 * no boot (mesmo nome que o reservation-service procura em stork://rentals) e deregistra
 * no shutdown, via adapter proprio (ConsulServiceRegistration) - o vicioso auto-registro
 * do Stork registra health-check-url relativo, critical desde o boot, e o dereg do rental
 * explodia no shutdown com "No CDI container is available".
 *
 * O catalogo e consultado por HTTP (API do Consul). Exige o oposto do defecto: check com
 * Status "passing" (o Consul alcanca o /q/health/live do JVM via host.docker.internal) e
 * saida limpa do catalogo apos o deregister.
 */
@QuarkusTest
@TestProfile(ConsulRegistrationProfile.class)
@QuarkusTestResource(value = ConsulRegistrationTestResource.class, restrictToAnnotatedClass = true)
class RentalRegistersInConsulTest {

    @Inject
    Config config;

    @Inject
    ConsulServiceRegistration registration;

    @Test
    void shouldPublishItselfWithPassingHealthCheckAndDeregister() throws Exception {
        int appPort = config.getValue("quarkus.http.test-port", int.class);

        JsonNode instances = awaitEntriesMatching(catalog(), node -> node.size() == 1, "registrar-se no Consul");
        JsonNode service = instances.get(0).get("Service");
        assertEquals("rentals", service.get("Service").asText());
        assertEquals(appPort, service.get("Port").asInt(), "a porta registrada e o listener HTTP real do JVM");
        assertEquals("host.docker.internal", service.get("Address").asText());

        awaitEntriesMatching(passingCatalog(), node -> node.size() == 1,
                "ter o health check 'passing' no catalogo (alcancavel pelo Consul)");
        JsonNode checks = instances.get(0).get("Checks");
        assertTrue(checks.isArray() && !checks.isEmpty(), "o registro tem checagem de saude no catalogo");

        registration.deregister();
        awaitCondition("sair do catalogo apos o deregister", this::catalogIsEmpty);
    }

    private JsonNode awaitEntriesMatching(URI uri, java.util.function.Predicate<JsonNode> matcher, String what)
            throws Exception {
        long deadline = System.currentTimeMillis() + 30_000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            last = new ObjectMapper().readTree(get(uri).body());
            if (matcher.test(last)) {
                return last;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("rental-service nao conseguiu " + what + " em 30s: " + last);
    }

    private void awaitCondition(String what, java.util.function.BooleanSupplier condition) throws Exception {
        long deadline = System.currentTimeMillis() + 15_000;
        while (System.currentTimeMillis() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            Thread.sleep(500);
        }
        throw new AssertionError("rental-service nao conseguiu " + what + " em 15s");
    }

    private boolean catalogIsEmpty() {
        try {
            HttpResponse<String> response = get(catalog());
            return response.statusCode() == 200 && new ObjectMapper().readTree(response.body()).isEmpty();
        } catch (Exception e) {
            return false;
        }
    }

    private HttpResponse<String> get(URI uri) throws Exception {
        return HttpClient.newHttpClient().send(
                HttpRequest.newBuilder().uri(uri).GET().build(),
                HttpResponse.BodyHandlers.ofString());
    }

    private URI catalog() {
        return URI.create("http://" + consulHost() + ":" + consulPort() + "/v1/health/service/rentals");
    }

    private URI passingCatalog() {
        return URI.create(catalog() + "?passing=true");
    }

    private String consulHost() {
        return config.getValue("acme.consul.registration.consul-host", String.class);
    }

    private int consulPort() {
        return config.getValue("acme.consul.registration.consul-port", int.class);
    }
}