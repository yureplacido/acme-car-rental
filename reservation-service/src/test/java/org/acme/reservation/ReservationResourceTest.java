package org.acme.reservation;

import io.quarkus.test.common.http.TestHTTPEndpoint;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.DisabledOnIntegrationTest;
import io.quarkus.test.junit.QuarkusMock;
import io.quarkus.test.junit.QuarkusTest;
import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.smallrye.mutiny.Uni;
import org.acme.reservation.api.ReservationResource;
import org.acme.reservation.client.inventory.Car;
import org.acme.reservation.client.inventory.GraphQLInventoryClient;
import org.acme.reservation.model.Reservation;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.net.URL;
import java.time.LocalDate;
import java.util.Collections;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;

/**
 * Livro 5.2.1 e 5.3.2: teste black-box via RestAssured + @TestHTTPEndpoint/@TestHTTPResource.
 *
 * <p>Nota sobre a abordagem de mock: o livro apresenta duas opções para substituir o
 * GraphQLInventoryClient — (5.3.1) um bean CDI alternativo com @Mock e (5.3.2) Mockito.
 * Elas são mutuamente exclusivas (o próprio livro adverte que o @Mock "conflita" com o
 * mock do Mockito), por isso seguimos apenas o Mockito, abaixo.</p>
 */
@QuarkusTest
public class ReservationResourceTest {

    // Injeta a base do endpoint: http://localhost:8181/reservations
    @TestHTTPEndpoint(ReservationResource.class)
    @TestHTTPResource
    URL reservationResource;

    // Graças ao @TestHTTPEndpoint, o valor "availability" é anexado à base:
    // http://localhost:8181/reservations/availability
    @TestHTTPEndpoint(ReservationResource.class)
    @TestHTTPResource("availability")
    URL availability;

    @Test
    public void testReservationIds() {
        Reservation reservation = new Reservation();
        reservation.setCarId(12345L);
        reservation.setStartDay(LocalDate.parse("2035-03-20"));
        reservation.setEndDay(LocalDate.parse("2035-03-29"));

        // POST /reservations: o id é atribuído pelo Panache e devolvido no JSON.
        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(reservation)
                .when().post(reservationResource)
                .then().statusCode(200)
                .body("id", notNullValue());
    }

    @Test
    @DisabledOnIntegrationTest(forArtifactTypes = DisabledOnIntegrationTest.ArtifactType.NATIVE_BINARY)
    public void testMakingAReservationAndCheckAvailability() {
        // Instala o mock ANTES de qualquer chamada HTTP, para que o ReservationResource
        // resolva o client GraphQL para o mock (e não faça rede até o Inventory).
        GraphQLInventoryClient mock = Mockito.mock(GraphQLInventoryClient.class);
        Car peugeot = new Car(1L, "ABC123", "Peugeot", "406");
        // Livro 7.44: o allCars agora devolve um Uni (fluxo reativo).
        Mockito.when(mock.allCars()).thenReturn(Uni.createFrom().item(Collections.singletonList(peugeot)));
        QuarkusMock.installMockForType(mock, GraphQLInventoryClient.class);

        // Datas em 2035: nunca são "hoje" (evita o fluxo de Rental em make()) e não
        // sobrepõem as demais reservas do banco.
        String startDate = "2035-01-01";
        String endDate = "2035-01-10";
        Car[] cars = RestAssured.given()
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .when().get(availability)
                .then().statusCode(200)
                .extract().as(Car[].class);
        Car car = cars[0];

        // Reserva o primeiro carro disponível para o mesmíssimo período consultado.
        Reservation reservation = new Reservation();
        reservation.setCarId(car.getId());
        reservation.setStartDay(LocalDate.parse(startDate));
        reservation.setEndDay(LocalDate.parse(endDate));

        RestAssured.given()
                .contentType(ContentType.JSON)
                .body(reservation)
                .when().post(reservationResource)
                .then().statusCode(200)
                .body("carId", is(car.getId().intValue()));

        // A janela consultada coincide com a reserva criada, logo o carro não pode
        // aparecer mais como disponível: filtro Groovy findAll + hasSize(0).
        RestAssured.given()
                .queryParam("startDate", startDate)
                .queryParam("endDate", endDate)
                .when().get(availability)
                .then().statusCode(200)
                .body("findAll { it.id == " + car.getId() + " }", hasSize(0));
    }
}