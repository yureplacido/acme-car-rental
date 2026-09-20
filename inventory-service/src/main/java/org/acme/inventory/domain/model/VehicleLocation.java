package org.acme.inventory.domain.model;

import java.util.Objects;

public record VehicleLocation(String branchCode, String city) {
    public VehicleLocation {
        requireText(branchCode, "branchCode");
        requireText(city, "city");
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
