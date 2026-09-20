package org.acme.inventory.domain.model;

import java.util.Locale;
import java.util.Objects;

public record LicensePlate(String value) {
    public LicensePlate {
        Objects.requireNonNull(value, "license plate is required");
        value = value.trim().toUpperCase(Locale.ROOT);
        if (value.isBlank()) {
            throw new IllegalArgumentException("license plate is required");
        }
    }
}
