package org.acme.inventory.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;

import java.util.List;
import java.util.Optional;

public interface VehicleRepository {
    Uni<List<Vehicle>> all();
    Uni<Optional<Vehicle>> findByLicensePlate(LicensePlate licensePlate);
    Uni<Vehicle> save(Vehicle vehicle);
}
