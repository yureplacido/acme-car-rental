package org.acme.inventory.domain.event;

import org.acme.inventory.domain.model.VehicleId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

public record VehicleRegistered(
        UUID eventId,
        int version,
        Instant occurredAt,
        VehicleId vehicleId,
        String licensePlate) {

    public VehicleRegistered {
        Objects.requireNonNull(eventId, "eventId is required");
        Objects.requireNonNull(occurredAt, "occurredAt is required");
        Objects.requireNonNull(vehicleId, "vehicleId is required");
        Objects.requireNonNull(licensePlate, "licensePlate is required");

        if (version < 1) {
            throw new IllegalArgumentException("event version must be positive");
        }
    }

    public static VehicleRegistered from(VehicleId vehicleId, String licensePlate) {
        return new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.now(),
                vehicleId,
                licensePlate);
    }
}
