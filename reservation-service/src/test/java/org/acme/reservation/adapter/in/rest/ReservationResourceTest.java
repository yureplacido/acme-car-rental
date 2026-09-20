package org.acme.reservation.adapter.in.rest;

import io.quarkus.test.junit.QuarkusTest;
import io.restassured.http.ContentType;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

@QuarkusTest
class ReservationResourceTest {

    @Test
    void shouldCreateReservationThroughHttp() {
        given()
                .contentType(ContentType.JSON)
                .body("""
                        {
                          "carId": 9001,
                          "startDay": "2035-06-01",
                          "endDay": "2035-06-10"
                        }
                        """)
                .when()
                .post("/reservations")
                .then()
                .statusCode(200)
                .body("id", notNullValue())
                .body("status", org.hamcrest.Matchers.equalTo("PENDING"));
    }
}
