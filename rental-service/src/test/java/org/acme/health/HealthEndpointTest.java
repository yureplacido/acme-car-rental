package org.acme.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthEndpointTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @Test
    void shouldExposeLiveness() throws Exception {
        assertHealth("/q/health/live");
    }

    @Test
    void shouldExposeReadiness() throws Exception {
        assertHealth("/q/health/ready");
    }

    @Test
    void shouldExposeStartup() throws Exception {
        assertHealth("/q/health/started");
    }

    private void assertHealth(String path) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + System.getProperty("quarkus.http.test-port") + path))
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains(""status":"UP""), response.body());
    }
}
