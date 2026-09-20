package org.acme.reservation.adapter.out.persistence;

import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

@QuarkusTest
class ReservationPersistenceTest {

    @Test
    @RunOnVertxContext
    void shouldPersistReservation(TransactionalUniAsserter asserter) {
        ReservationEntity entity = new ReservationEntity();
        entity.carId = 384L;
        entity.userId = "alice";
        entity.startDay = LocalDate.of(2035, 5, 1);
        entity.endDay = LocalDate.of(2035, 5, 7);

        asserter.<ReservationEntity>assertThat(
                entity::persist,
                persisted -> {
                    assertNotNull(persisted.id);
                    assertEquals("alice", persisted.userId);
                });
    }
}
