package org.acme.reservation.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CarPage {

    private List<Car> items;
    private long total;
    private int offset;
    private int limit;
    private boolean hasNextPage;
}