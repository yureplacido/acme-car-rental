package org.acme.inventory.application.query;

public record VehicleSearch(
        int offset,
        int limit,
        String search,
        VehicleFilter filter,
        VehicleSortField sort,
        SortDirection direction) {

    public VehicleSearch {
        if (offset < 0) {
            throw new IllegalArgumentException("offset cannot be negative");
        }
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        search = normalize(search);
    }

    private static String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
