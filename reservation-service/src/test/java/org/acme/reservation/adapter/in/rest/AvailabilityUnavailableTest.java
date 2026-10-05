package org.acme.reservation.adapter.in.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import org.acme.reservation.application.exception.InventoryUnavailable;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.acme.reservation.application.query.AvailableVehicle;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.when;

/**
 * O fallback da leitura nao degrada a resposta: quando o inventory esta fora, a fronteira
 * responde 503 com um corpo estavel, em vez de 200 com lista vazia (que o cliente leria como
 * "nao ha veiculo disponivel").
 *
 * Decisao e evidencia em docs/adr/009-fault-tolerance-chamadas-externas.md.
 */
@QuarkusTest
class AvailabilityUnavailableTest {

    @InjectMock
    InventoryGateway inventoryGateway;

    @InjectMock
    ReservationRepository reservationRepository;

    @Test
    void shouldAnswerServiceUnavailableWhenInventoryCannotBeReached() {
        givenInventory(new InventoryUnavailable(new IllegalStateException("inventory is restarting")));

        given()
                .queryParam("startDate", LocalDate.of(2035, 6, 1).toString())
                .queryParam("endDate", LocalDate.of(2035, 6, 10).toString())
                .when()
                .get("/reservations/availability")
                .then()
                .statusCode(503)
                .header("Retry-After", equalTo("30"))
                .body("code", equalTo("INVENTORY_UNAVAILABLE"))
                .body("message", equalTo("vehicle inventory is temporarily unavailable"))
                .body("retryAfterSeconds", equalTo(30));
    }

    @Test
    void shouldAnswerTheFleetWhenInventoryIsReachable() {
        when(inventoryGateway.findVehicles()).thenReturn(Uni.createFrom().item(List.of(
                new AvailableVehicle(10L, "AAA-1", "Renault", "Clio"))));
        when(reservationRepository.all()).thenReturn(Uni.createFrom().item(List.of()));

        given()
                .queryParam("startDate", LocalDate.of(2035, 6, 1).toString())
                .queryParam("endDate", LocalDate.of(2035, 6, 10).toString())
                .when()
                .get("/reservations/availability")
                .then()
                .statusCode(200)
                .body("[0].id", equalTo(10))
                .body("[0].licensePlateNumber", equalTo("AAA-1"));
    }

    @Test
    void shouldNotLeakInfrastructureDetailWhenInventoryIsUnavailable() {
        givenInventory(new InventoryUnavailable(new IllegalStateException(
                "connection refused to http://inventory-service:8083/graphql")));

        String body = given()
                .queryParam("startDate", LocalDate.of(2035, 6, 1).toString())
                .queryParam("endDate", LocalDate.of(2035, 6, 10).toString())
                .when()
                .get("/reservations/availability")
                .then()
                .statusCode(503)
                .extract()
                .asString();

        assertFalse(body.contains("inventory-service"), body);
    }

    private void givenInventory(Throwable failure) {
        when(inventoryGateway.findVehicles()).thenReturn(Uni.createFrom().failure(failure));
        when(reservationRepository.all()).thenReturn(Uni.createFrom().item(List.of()));
    }
}
