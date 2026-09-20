package org.acme.reservation.adapter.out.inventory;

import org.acme.reservation.adapter.out.inventory.model.Car;

import java.util.List;

public record CarPage(
        List<Car> items,
        long total,
        int offset,
        int limit,
        boolean hasNextPage) {
}
