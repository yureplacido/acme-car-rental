package org.acme.inventory.adapter.in.graphql.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Description("Veículo disponibilizado pelo inventário")
public class VehicleView {
    @Id
    @NonNull
    private Long id;
    @NonNull
    private String manufacturer;
    @NonNull
    private String model;
    @NonNull
    @Name("plateNumber")
    private String licensePlateNumber;
    private String status;
    private String category;
    private String transmission;
    private String fuelType;
    private Integer year;
    private String color;
    private Integer seats;
    private BigDecimal dailyRate;
    private String branchCode;
    private String city;
}
