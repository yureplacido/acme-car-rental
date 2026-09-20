package org.acme.reservation.adapter.in.rest.model;

import org.acme.reservation.domain.model.Reservation;

import java.time.LocalDate;

public record ReservationResponse(
        Long id,
        String userId,
        Long carId,
        LocalDate startDay,
        LocalDate endDay,
        String status) {

    public static ReservationResponse from(Reservation reservation) {
        return new ReservationResponse(
                reservation.id().value(),
                reservation.customerId().value(),
                reservation.vehicleId().value(),
                reservation.period().start(),
                reservation.period().end(),
                reservation.status().name());
    }
}
