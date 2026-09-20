package org.acme.reservation;

import io.quarkus.test.hibernate.reactive.panache.TransactionalUniAsserter;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import org.acme.reservation.entity.ReservationEntity;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Livro 7.7 - teste white-box da persistência reativa (Hibernate Reactive + Panache).
 *
 * <p>Substitui o {@code ReservationRepositoryTest} do cap.5 (repositório in-memory):
 * o teste agora roda no event loop Vert.x ({@link RunOnVertxContext}) e usa o
 * {@link TransactionalUniAsserter}, que envolve cada asserção assíncrona em sua
 * própria transação, para persistir e reler a reserva no PostgreSQL (Dev Services).</p>
 */
@QuarkusTest
public class ReservationPersistenceTest {

    @Test
    @RunOnVertxContext
    public void testCreateReservation(TransactionalUniAsserter asserter) {
        ReservationEntity reservation = new ReservationEntity();
        reservation.startDay = LocalDate.now().plus(5, ChronoUnit.DAYS);
        reservation.endDay = LocalDate.now().plus(12, ChronoUnit.DAYS);
        reservation.carId = 384L;

        asserter.<ReservationEntity>assertThat(() -> reservation.persist(),
                r -> {
                    Assertions.assertNotNull(r.id);
                    asserter.putData("reservation.id", r.id);
                });

        // Localiza pela chave e valida os campos persistidos.
        asserter.<ReservationEntity>assertThat(() -> ReservationEntity.findById(asserter.getData("reservation.id")),
                persistedReservation -> {
                    Assertions.assertNotNull(persistedReservation);
                    Assertions.assertEquals(reservation.carId, persistedReservation.carId);
                });

        // A reserva criada consta na listagem completa.
        asserter.<Boolean>assertThat(() -> ReservationEntity.<ReservationEntity>listAll().map(book ->
                        book.stream().anyMatch(r -> r.id.equals(asserter.getData("reservation.id")))),
                found -> Assertions.assertTrue(found));
    }
}