package org.acme.inventory.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;

@QuarkusTest
class ReactiveExecutionResourceTest {

    @Test
    void shouldExecuteReactiveEndpointOnEventLoop() {
        given()
                .when()
                .get("/reactive/execution/event-loop")
                .then()
                .statusCode(200)
                .body(containsString("vert.x-eventloop-thread"));
    }

    @Test
    void shouldExecuteBlockingEndpointOnWorkerPool() {
        given()
                .when()
                .get("/reactive/execution/worker")
                .then()
                .statusCode(200)
                .body(containsString("executor-thread"));
    }
}
