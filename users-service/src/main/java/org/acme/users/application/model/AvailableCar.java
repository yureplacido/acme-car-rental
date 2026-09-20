package org.acme.users.application.model;

public record AvailableCar(
        Long id,
        String licensePlateNumber,
        String manufacturer,
        String model) {
}
