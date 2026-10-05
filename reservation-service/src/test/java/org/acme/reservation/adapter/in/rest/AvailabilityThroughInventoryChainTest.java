package org.acme.reservation.adapter.in.rest;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.graphql.client.GraphQLClientException;
import io.smallrye.graphql.client.InvalidResponseException;
import io.smallrye.mutiny.Uni;
import org.acme.reservation.adapter.out.inventory.GraphQLInventoryClient;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.equalTo;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Cadeia inteira da leitura de disponibilidade: recurso HTTP -> use case -> gateway real com
 * fault tolerance -> client GraphQL. Aqui nao ha mock da porta, so do cliente, entao o que passa
 * aqui e a policy como ela roda de verdade em CDI: timeout e retry do {@code %test} aplicados,
 * fallback na lista de tipos correta, e o mapper traduzindo em 503.
 *
 * <p>Complementa {@code AvailabilityUnavailableTest}, que fixa o contrato HTTP a partir da porta,
 * e {@code GraphQLInventoryGatewayFaultToleranceTest}, que fixa a policy com todos os cenarios.
 *
 * <p>Decisao e evidencia em docs/adr/009-fault-tolerance-chamadas-externas.md.
 */
@QuarkusTest
@Timeout(30)
class AvailabilityThroughInventoryChainTest {

    private static final LocalDate START = LocalDate.of(2035, 6, 1);
    private static final LocalDate END = LocalDate.of(2035, 6, 10);

    @InjectMock
    GraphQLInventoryClient inventoryClient;

    @InjectMock
    ReservationRepository reservationRepository;

    @Test
    void shouldAnswerServiceUnavailableWhenInventoryStopsAnsweringInGraphql() {
        AtomicInteger attempts = new AtomicInteger();
        when(inventoryClient.allCars()).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return Uni.createFrom().failure(
                    new InvalidResponseException("Unexpected response. Code=503, message=\"Service Unavailable\""));
        });
        givenNoExistingReservations();

        givenAvailability()
                .then()
                .statusCode(503)
                .header("Retry-After", equalTo("30"))
                .body("code", equalTo("INVENTORY_UNAVAILABLE"))
                .body("retryAfterSeconds", equalTo(30));

        assertEquals(3, attempts.get(), "a policy de leitura repete duas vezes antes de sinalizar");
    }

    @Test
    void shouldAnswerAnEmptyListWhenInventoryHasNoCars() {
        when(inventoryClient.allCars()).thenReturn(Uni.createFrom().item(List.of()));
        givenNoExistingReservations();

        givenAvailability()
                .then()
                .statusCode(200)
                .body("", empty());
    }

    /**
     * O erro de GraphQL e defeito do inventario, nao indisponibilidade: a fronteira nao pode
     * disfarçar isso como 503, que o cliente leria como "resultado inconclusivo, tente mais
     * tarde". Hoje sai como erro inesperado do proprio servico — este teste fixa esse "hoje",
     * e mudar o mapeamento e um item proprio.
     */
    @Test
    void shouldNotDisguiseGraphqlErrorsAsInventoryUnavailable() {
        AtomicInteger attempts = new AtomicInteger();
        when(inventoryClient.allCars()).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return Uni.createFrom().failure(new GraphQLClientException("schema exploded", List.of()));
        });
        givenNoExistingReservations();

        givenAvailability()
                .then()
                .statusCode(500);

        assertEquals(1, attempts.get(), "erro de GraphQL nao e transitorio: repetir nao muda nada");
    }

    private void givenNoExistingReservations() {
        when(reservationRepository.all()).thenReturn(Uni.createFrom().item(List.of()));
    }

    private static io.restassured.response.Response givenAvailability() {
        return given()
                .queryParam("startDate", START.toString())
                .queryParam("endDate", END.toString())
                .when()
                .get("/reservations/availability");
    }
}
