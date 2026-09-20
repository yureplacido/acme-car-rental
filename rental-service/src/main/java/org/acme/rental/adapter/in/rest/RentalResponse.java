package org.acme.rental.adapter.in.rest;

import org.acme.rental.domain.model.Rental;

import java.time.LocalDate;

public record RentalResponse(
        String id,
        String userId,
        Long reservationId,
        LocalDate startDate,
        LocalDate endDate,
        String status) {

    public static RentalResponse from(Rental rental) {
        return new RentalResponse(
                rental.id() == null ? null : rental.id().value(),
                rental.customerId().value(),
                rental.reservationId().value(),
                rental.startDate(),
                rental.endDate(),
                rental.status().name());
    }
}
