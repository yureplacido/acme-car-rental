package org.acme.inventory.domain.model;

import java.util.Objects;

public record VehicleSpecifications(
        String manufacturer,
        String model,
        VehicleCategory category,
        Transmission transmission,
        FuelType fuelType,
        Integer year,
        String color,
        Integer seats) {

    public VehicleSpecifications {
        requireText(manufacturer, "manufacturer");
        requireText(model, "model");
        if (year != null && year < 1886) {
            throw new IllegalArgumentException("year is invalid");
        }
        if (seats != null && seats < 1) {
            throw new IllegalArgumentException("seats must be positive");
        }
    }

    private static void requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
