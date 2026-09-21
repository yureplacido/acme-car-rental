package org.acme.billing.application.event;

import java.time.Instant;
import java.util.UUID;

public record VehicleRegistered(
        UUID eventId,
        int version,
        Instant occurredAt,
        VehicleId vehicleId,
        String licensePlate) {

    public record VehicleId(long value) {
    }
}
