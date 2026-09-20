package org.acme.inventory.adapter.in.graphql.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Name;

@Data
@NoArgsConstructor
public class CarInput {
    @Name("plateNumber")
    private String licensePlateNumber;
    private String manufacturer;
    private String model;
    private String category;
    private String transmission;
    private String fuelType;
    private Integer year;
    private String color;
    private Integer seats;
    private String branchCode;
    private String city;
    private java.math.BigDecimal dailyRate;
    private String currency;
}
