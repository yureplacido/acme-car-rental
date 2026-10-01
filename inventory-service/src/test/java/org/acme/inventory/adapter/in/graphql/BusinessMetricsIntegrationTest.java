package org.acme.inventory.adapter.in.graphql;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class BusinessMetricsIntegrationTest {

    private static final Pattern REGISTERED_COUNTER =
            Pattern.compile("inventory_vehicles_registered_total\\s+([0-9.]+)");

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

        String plate = "OBS" + System.nanoTime();
        String graphqlQuery = "mutation { register(input: { plateNumber: \"" + plate + "\", manufacturer: \"Ford\", model: \"Mustang\", category: \"SUV\", year: 2025, color: \"black\", seats: 5, dailyRate: 149.90, currency: \"BRL\" }) { id plateNumber } }";

        given()
                .contentType("application/json")
                .body(new java.util.HashMap<String, Object>() {{ put("query", graphqlQuery); }})
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
        Matcher matcher = REGISTERED_COUNTER.matcher(metrics);
        if (!matcher.find()) {
            return 0.0;
        }
        return Double.parseDouble(matcher.group(1));
    }
}
