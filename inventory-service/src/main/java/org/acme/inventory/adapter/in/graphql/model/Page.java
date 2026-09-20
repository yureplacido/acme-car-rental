package org.acme.inventory.adapter.in.graphql.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Page<T> {
    public static final int MAX_LIMIT = 100;
    private List<T> items;
    private long total;
    private int offset;
    private int limit;
    private boolean hasNextPage;
}
