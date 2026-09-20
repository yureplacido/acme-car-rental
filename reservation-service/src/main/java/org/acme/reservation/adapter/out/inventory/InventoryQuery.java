package org.acme.reservation.adapter.out.inventory;

import org.acme.reservation.adapter.out.inventory.model.Car;

import java.util.List;

public record InventoryQuery(
        int offset,
        int limit,
        String search,
        CarFilter filter,
        CarSortField sort,
        SortOrder order,
        List<String> fields) {
}
