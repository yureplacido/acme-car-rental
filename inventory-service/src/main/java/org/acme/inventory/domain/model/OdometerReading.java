package org.acme.inventory.domain.model;

public record OdometerReading(long kilometers) {
    public OdometerReading {
        if (kilometers < 0) {
            throw new IllegalArgumentException("odometer cannot be negative");
        }
    }
}
