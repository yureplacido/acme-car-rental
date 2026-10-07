package org.acme.reservation.adapter.out.registration;

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
 * Cap.10 item 9 - service discovery: o reservation-service deixa de ser localizavel
 * por uma URL fixa neste app e passa a se publicar no Consul como "reservations" no boot.
 *
 * O catalogo e consultado por HTTP (API do Consul) porque o registro e auto: feito pelo
 * Stork no runtime init do Quarkus, antes de qualquer bean de aplicacao existir.
 */
@QuarkusTest
@TestProfile(ConsulRegistrationProfile.class)
@QuarkusTestResource(value = ConsulRegistrationTestResource.class, restrictToAnnotatedClass = true)
class ReservationRegistersInConsulTest {

    @Inject
    Config config;

    @Test
    void shouldPublishItselfAsReservationsInConsul() throws Exception {
        JsonNode instances = awaitRegistrationInCatalog();

        assertEquals(1, instances.size(), "uma unica instancia: o proprio reservation-service");
        JsonNode service = instances.get(0).get("Service");
        assertEquals("reservations", service.get("Service").asText());
        assertEquals(18091, service.get("Port").asInt());
        assertTrue(service.has("Address") && !service.get("Address").asText().isBlank(),
                "o IP detectado na rede do host fica registrado como Address");
        JsonNode checks = instances.get(0).get("Checks");
        assertTrue(checks.isArray() && !checks.isEmpty(), "o registro tem checagem de saude no catalogo");
    }

    private JsonNode awaitRegistrationInCatalog() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        URI catalog = URI.create("http://" + consulHost() + ":" + consulPort()
                + "/v1/health/service/reservations");
        long deadline = System.currentTimeMillis() + 15_000;
        JsonNode last = null;
        while (System.currentTimeMillis() < deadline) {
            HttpResponse<String> response = client.send(
                    HttpRequest.newBuilder().uri(catalog).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            last = new ObjectMapper().readTree(response.body());
            if (last.isArray() && !last.isEmpty()) {
                return last;
            }
            Thread.sleep(300);
        }
        throw new AssertionError("reservation-service nao se registrou no Consul em 15s: " + last);
    }

    private String consulHost() {
        return config.getValue("quarkus.stork.reservations.service-registrar.consul-host", String.class);
    }

    private int consulPort() {
        return config.getValue("quarkus.stork.reservations.service-registrar.consul-port", int.class);
    }
}