package org.acme.inventory.application.usecase;

import io.smallrye.mutiny.Uni;
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

    public Uni<Optional<Vehicle>> handle(String licensePlate) {
        return repository.findByLicensePlate(new LicensePlate(licensePlate))
                .flatMap(optional -> optional
                        .map(vehicle -> repository.save(vehicle.decommission()).map(Optional::of))
                        .orElseGet(() -> Uni.createFrom().item(Optional.empty())));
    }
}
