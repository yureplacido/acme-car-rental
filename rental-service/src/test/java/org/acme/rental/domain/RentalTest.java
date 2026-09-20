package org.acme.rental.domain;

import org.acme.rental.domain.model.CustomerId;
import org.acme.rental.domain.model.Rental;
import org.acme.rental.domain.model.RentalStatus;
import org.acme.rental.domain.model.ReservationId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class RentalTest {

    @Test
    void shouldStartActiveRental() {
        Rental rental = Rental.start(
                new CustomerId("alice"),
                new ReservationId(10L),
                LocalDate.of(2035, 3, 20));

        assertEquals(RentalStatus.ACTIVE, rental.status());
        assertEquals(LocalDate.of(2035, 3, 20), rental.startDate());
        assertNull(rental.endDate());
    }

    @Test
    void shouldFinishActiveRental() {
        Rental rental = Rental.start(
                new CustomerId("alice"),
                new ReservationId(10L),
                LocalDate.of(2035, 3, 20));

        rental.finish(LocalDate.of(2035, 3, 22));

        assertEquals(RentalStatus.COMPLETED, rental.status());
        assertEquals(LocalDate.of(2035, 3, 22), rental.endDate());
    }

    @Test
    void shouldNotFinishCompletedRentalTwice() {
        Rental rental = Rental.start(
                new CustomerId("alice"),
                new ReservationId(10L),
                LocalDate.of(2035, 3, 20));
        rental.finish(LocalDate.of(2035, 3, 22));

        assertThrows(IllegalStateException.class,
                () -> rental.finish(LocalDate.of(2035, 3, 23)));
    }
}
