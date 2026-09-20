package org.acme.inventory.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;

import java.util.Optional;

@ApplicationScoped
public class DecommissionVehicle {

    private final VehicleRepository repository;

    @Inject
    public DecommissionVehicle(VehicleRepository repository) {
        this.repository = repository;
    }

    public Optional<Vehicle> handle(String licensePlate) {
        return repository.findByLicensePlate(new LicensePlate(licensePlate))
                .map(vehicle -> repository.save(vehicle.decommission()));
    }
}
