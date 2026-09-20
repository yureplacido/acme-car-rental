package org.acme.users.application.model;

import java.time.LocalDate;

public record ReservationView(
        Long id,
        String userId,
        Long carId,
        LocalDate startDay,
        LocalDate endDay,
        String status) {
}
