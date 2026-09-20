package org.acme.reservation.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public record RentalPeriod(LocalDate start, LocalDate end) {

    public RentalPeriod {
        Objects.requireNonNull(start, "start date is required");
        Objects.requireNonNull(end, "end date is required");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end date cannot be before start date");
        }
    }

    public boolean overlaps(RentalPeriod other) {
        Objects.requireNonNull(other);
        return !end.isBefore(other.start) && !start.isAfter(other.end);
    }
}
