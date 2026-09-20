package org.acme.users.adapter.out.reservation.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class Car {
    private Long id;
    private String licensePlateNumber;
    private String manufacturer;
    private String model;
}
