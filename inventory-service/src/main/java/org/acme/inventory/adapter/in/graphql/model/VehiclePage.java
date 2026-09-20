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
public class VehiclePage {
    private List<VehicleView> items;
    private long total;
    private int offset;
    private int limit;
    private boolean hasNextPage;
}
