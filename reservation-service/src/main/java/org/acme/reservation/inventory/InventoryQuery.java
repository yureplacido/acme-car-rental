package org.acme.reservation.inventory;

import java.util.Collection;

public record InventoryQuery(int offset,
                             int limit,
                             String search,
                             CarFilter filter,
                             CarSortField sort,
                             SortOrder order,
                             Collection<String> fields) {

    public static InventoryQuery base(int offset, int limit, Collection<String> fields) {
        return new InventoryQuery(offset, limit, null, null, null, null, fields);
    }
}