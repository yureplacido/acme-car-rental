package org.acme.reservation.domain;

import org.acme.reservation.domain.model.CustomerId;
import org.acme.reservation.domain.model.RentalPeriod;
import org.acme.reservation.domain.model.Reservation;
import org.acme.reservation.domain.model.ReservationStatus;
import org.acme.reservation.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class ReservationTest {

    @Test
    void shouldCreatePendingReservation() {
        Reservation reservation = reservation();

        assertEquals(ReservationStatus.PENDING, reservation.status());
        assertTrue(reservation.active());
    }

    @Test
    void shouldConfirmPendingReservation() {
        Reservation reservation = reservation();

        reservation.confirm();

        assertEquals(ReservationStatus.CONFIRMED, reservation.status());
    }

    @Test
    void shouldRejectInvalidRentalPeriod() {
        assertThrows(IllegalArgumentException.class,
                () -> new RentalPeriod(
                        LocalDate.of(2035, 1, 10),
                        LocalDate.of(2035, 1, 1)));
    }

    @Test
    void shouldDetectOverlappingRentalPeriods() {
        RentalPeriod first = new RentalPeriod(
                LocalDate.of(2035, 1, 1),
                LocalDate.of(2035, 1, 10));
        RentalPeriod second = new RentalPeriod(
                LocalDate.of(2035, 1, 10),
                LocalDate.of(2035, 1, 20));

        assertTrue(first.overlaps(second));
    }

    @Test
    void shouldDetectConflictBetweenActiveReservationsForSameVehicle() {
        Reservation first = reservation();
        Reservation second = Reservation.create(
                new CustomerId("bob"),
                new VehicleId(10L),
                new RentalPeriod(
                        LocalDate.of(2035, 3, 5),
                        LocalDate.of(2035, 3, 8)));

        assertTrue(first.conflictsWith(second));
    }

    @Test
    void shouldNotDetectConflictForDifferentVehicles() {
        Reservation first = reservation();
        Reservation second = Reservation.create(
                new CustomerId("bob"),
                new VehicleId(11L),
                first.period());

        assertFalse(first.conflictsWith(second));
    }

    private Reservation reservation() {
        return Reservation.create(
                new CustomerId("alice"),
                new VehicleId(10L),
                new RentalPeriod(
                        LocalDate.of(2035, 3, 20),
                        LocalDate.of(2035, 3, 29)));
    }
}
