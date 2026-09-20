package org.acme.inventory.application.query;

import org.acme.inventory.domain.model.Vehicle;

import java.util.List;

public record VehiclePage(
        List<Vehicle> items,
        long total,
        int offset,
        int limit,
        boolean hasNextPage) {
}
