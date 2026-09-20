package org.acme.reservation.adapter.in.rest.model;

import java.time.LocalDate;

public record ReservationRequest(
        Long carId,
        LocalDate startDay,
        LocalDate endDay) {
}
