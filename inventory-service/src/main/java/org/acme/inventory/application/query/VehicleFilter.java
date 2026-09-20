package org.acme.inventory.application.query;

import org.acme.inventory.domain.model.VehicleStatus;

public record VehicleFilter(
        String manufacturer,
        String model,
        String plate,
        VehicleStatus status) {
}
