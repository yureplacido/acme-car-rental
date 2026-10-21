package org.acme.users.adapter.in.web;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import org.acme.users.application.exception.AvailabilityUnavailable;
import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.usecase.ReservationFacade;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;
import java.util.List;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Contrato HTTP do {@code GET /users/available} quando a disponibilidade e inconclusiva: a
 * fronteira responde 503 com o fragmento explicativo, em vez de 200 com a tabela vazia (que o
 * usuario leria como "nenhum veiculo").
 *
 * <p>Decisao registrada em docs/adr/009-fault-tolerance-chamadas-externas.md.
 */
@QuarkusTest
@Timeout(30)
class ReservationsAvailabilityUnavailableTest {

    @InjectMock
    ReservationFacade facade;

    @Test
    void shouldAnswerServiceUnavailableWithTheExplanatoryFragment() {
        when(facade.availableCars(any(), any()))
                .thenThrow(new AvailabilityUnavailable(new IllegalStateException("reservations is down")));

        given()
                .queryParam("startDate", LocalDate.of(2035, 6, 1).toString())
                .queryParam("endDate", LocalDate.of(2035, 6, 10).toString())
                .when()
                .get("/available")
                .then()
                .statusCode(503)
                .header("Retry-After", equalTo("30"))
                .body(containsString("Availability temporarily unavailable"));
    }

    @Test
    void shouldAnswerTheFleetWhenAvailabilityIsReachable() {
        when(facade.availableCars(any(), any()))
                .thenReturn(List.of(new AvailableCar(10L, "AAA-1", "Renault", "Clio")));

        given()
                .queryParam("startDate", LocalDate.of(2035, 6, 1).toString())
                .queryParam("endDate", LocalDate.of(2035, 6, 10).toString())
                .when()
                .get("/available")
                .then()
                .statusCode(200)
                .body(containsString("AAA-1"));
    }

    @Test
    void shouldNotLeakInfrastructureDetailWhenAvailabilityIsUnavailable() {
        when(facade.availableCars(any(), any()))
                .thenThrow(new AvailabilityUnavailable(new IllegalStateException(
                        "connection refused to http://reservation-service:8081/reservations/availability")));

        String body = given()
                .queryParam("startDate", LocalDate.of(2035, 6, 1).toString())
                .queryParam("endDate", LocalDate.of(2035, 6, 10).toString())
                .when()
                .get("/available")
                .then()
                .statusCode(503)
                .extract()
                .asString();

        assertFalse(body.contains("reservation-service"), body);
        assertFalse(body.contains("reservations is down"), body);
    }
}
