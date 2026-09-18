package org.acme.reservation.inventory;

import jakarta.json.bind.annotation.JsonbProperty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Car {

    private Long id;
    @JsonbProperty("plateNumber")
    private String licensePlateNumber;
    private String manufacturer;
    private String model;
}