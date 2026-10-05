package org.acme.reservation.adapter.in.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.acme.reservation.adapter.out.rental.RentalClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Contrato de falha da escrita: o que o cliente recebe quando a locação não pode ser iniciada.
 *
 * <p>Cadeia real com {@code @Retry} fora: {@code POST /reservations} →
 * {@code CreateReservation} → {@code RentalRestGateway} (com o {@code @Timeout} de 2 s) → client
 * REST mockado. Só entra na escrita a reserva que começa hoje — é quando {@code CreateReservation}
 * chama o rental-service.
 *
 * <p>O que importa aqui é que a falha da escrita <b>não</b> vira o 503 de
 * {@code InventoryUnavailableMapper}: aquele sinal significa "catálogo inconclusivo", e a escrita
 * precisa de outra resposta.
 *
 * <p>Caracterização: hoje a fronteira devolve 500 (erro inesperado do próprio serviço) quando o
 * rental-service estoura o prazo ou recusa a chamada. Um status dedicado para "a escrita depende de
 * um serviço que não respondeu" é contrato novo, e fica registrado como dívida na ADR 009.
 */
@QuarkusTest
@Timeout(30)
class ReservationWriteFailureTest {

    @Test
    void shouldFailWithoutRetryingWhenTheRentalServiceNeverAnswers() {
        when(rentalClient.start(anyString(), anyLong())).thenReturn(Uni.createFrom().nothing());

        postReservationStartingToday(9002)
                .then()
                .statusCode(500);

        verify(rentalClient, times(1)).start(anyString(), anyLong());
    }

    @Test
    void shouldFailWithoutRetryingWhenTheRentalServiceRejectsTheCall() {
        when(rentalClient.start(anyString(), anyLong()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("rental is down")));

        postReservationStartingToday(9003)
                .then()
                .statusCode(500);

        verify(rentalClient, times(1)).start(anyString(), anyLong());
    }

    /**
     * A dívida fica visível pela própria API: a reserva existe em PENDING depois da falha da
     * escrita. É o que o ADR 009 chama de reconciliação pendente — e o que impede tratar
     * "escrevi e falhei" como se nada tivesse sido gravado.
     */
    @Test
    void shouldLeaveTheReservationVisibleAsPendingAfterTheWriteFails() {
        when(rentalClient.start(anyString(), anyLong()))
                .thenReturn(Uni.createFrom().failure(new IllegalStateException("rental is down")));

        postReservationStartingToday(9004)
                .then()
                .statusCode(500);

        given()
                .when()
                .get("/reservations/all")
                .then()
                .statusCode(200)
                .body("find { it.carId == 9004 }.status", equalTo("PENDING"));
    }

    @InjectMock
    @RestClient
    RentalClient rentalClient;

    private static io.restassured.response.Response postReservationStartingToday(long carId) {
        LocalDate today = LocalDate.now();
        return given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "carId": %d,
                          "startDay": "%s",
                          "endDay": "%s"
                        }
                        """.formatted(carId, today, today.plusDays(9)))
                .when()
                .post("/reservations");
    }
}
