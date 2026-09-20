package org.acme.rental.domain.model;

import java.util.Objects;

public record CustomerId(String value) {
    public CustomerId {
        Objects.requireNonNull(value, "customer id is required");
        if (value.isBlank()) throw new IllegalArgumentException("customer id is required");
    }
}
