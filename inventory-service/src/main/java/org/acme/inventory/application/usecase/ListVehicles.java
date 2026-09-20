package org.acme.inventory.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.domain.model.Vehicle;

import java.util.List;

@ApplicationScoped
public class ListVehicles {

    private final VehicleRepository repository;

    @Inject
    public ListVehicles(VehicleRepository repository) {
        this.repository = repository;
    }

    public Uni<List<Vehicle>> handle() {
        return repository.all();
    }
}
