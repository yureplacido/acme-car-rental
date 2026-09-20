package org.acme.reservation.adapter.out.rental;

public record RentalResponse(
        String id,
        String customerId,
        Long reservationId,
        String startDate,
        String endDate,
        String status) {
}
