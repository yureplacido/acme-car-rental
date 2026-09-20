package org.acme.inventory.domain.model;

public record VehicleId(Long value) {
    public VehicleId {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("vehicle id must be positive");
        }
    }
}
