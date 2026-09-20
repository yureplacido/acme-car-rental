package org.acme.rental.domain.model;

public record RentalId(String value) {
    public RentalId {
        if (value != null && value.isBlank()) {
            throw new IllegalArgumentException("rental id cannot be blank");
        }
    }
}
