package org.acme.inventory.adapter.in.graphql.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarFilter {
    private String manufacturer;
    private String model;
    private String plate;
    private CarStatus status;
}
