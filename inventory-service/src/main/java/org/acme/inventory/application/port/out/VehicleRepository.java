package org.acme.inventory.application.port.out;

import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository {
    List<Vehicle> findAll();
    Optional<Vehicle> findByLicensePlate(LicensePlate licensePlate);
    Vehicle save(Vehicle vehicle);
}
