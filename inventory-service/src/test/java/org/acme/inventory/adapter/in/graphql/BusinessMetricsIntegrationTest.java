package org.acme.inventory.adapter.in.graphql;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BusinessMetricsIntegrationTest {

    @Test
    void shouldExposeVehicleRegistrationCounterAfterGraphQLMutation() {
        String beforeMetrics = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        double before = counterValue(beforeMetrics);

        String mutation = """
                {
                  "query": "mutation { register(input: { plateNumber: \"OBS%s\", manufacturer: \"Ford\", model: \"Mustang\", category: \"SUV\", year: 2025, color: \"black\", seats: 5, dailyRate: 149.90, currency: \"BRL\" }) { id plateNumber } }"
                }
                """.formatted(System.nanoTime());

        given()
                .contentType("application/json")
                .body(mutation)
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body(containsString("plateNumber"));

        String afterMetrics = given()
                .when()
                .get("/q/metrics")
                .then()
                .statusCode(200)
                .extract()
                .asString();

        assertEquals(before + 1.0, counterValue(afterMetrics), 0.0, afterMetrics);
    }

    private double counterValue(String metrics) {
        String prefix = "inventory_vehicles_registered_total ";
        return metrics.lines()
                .filter(line -> line.startsWith(prefix))
                .map(line -> line.substring(prefix.length()).trim())
                .mapToDouble(Double::parseDouble)
                .findFirst()
                .orElse(0.0);
    }
}
