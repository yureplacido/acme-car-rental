package org.acme.rental.domain.model;

public record ReservationId(Long value) {
    public ReservationId {
        if (value == null || value <= 0) throw new IllegalArgumentException("reservation id must be positive");
    }
}
