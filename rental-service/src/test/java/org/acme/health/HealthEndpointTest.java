package org.acme.health;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

@QuarkusTest
class HealthEndpointTest {

    private final HttpClient client = HttpClient.newHttpClient();

    @TestHTTPResource("/q/health/live")
    URI livenessUrl;

    @TestHTTPResource("/q/health/ready")
    URI readinessUrl;

    @TestHTTPResource("/q/health/started")
    URI startupUrl;

    @Test
    void shouldExposeLiveness() throws Exception {
        assertHealth(livenessUrl);
    }

    @Test
    void shouldExposeReadiness() throws Exception {
        assertHealth(readinessUrl);
    }

    @Test
    void shouldExposeStartup() throws Exception {
        assertHealth(startupUrl);
    }

    private void assertHealth(URI uri) throws Exception {
        var request = HttpRequest.newBuilder()
                .uri(uri)
                .GET()
                .build();

        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("\"status\":\"UP\""), response.body());
    }
}
