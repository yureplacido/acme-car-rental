package org.acme.inventory.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleLocation;
import org.acme.inventory.domain.model.VehicleSpecifications;

@ApplicationScoped
public class RegisterVehicle {

    private final VehicleRepository repository;

    @Inject
    public RegisterVehicle(VehicleRepository repository) {
        this.repository = repository;
    }

    public Vehicle handle(Command command) {
        Vehicle vehicle = Vehicle.register(
                new LicensePlate(command.licensePlateNumber()),
                new VehicleSpecifications(
                        command.manufacturer(),
                        command.model(),
                        command.category(),
                        command.transmission(),
                        command.fuelType(),
                        command.year(),
                        command.color(),
                        command.seats()),
                command.location());
        return repository.save(vehicle);
    }

    public record Command(
            String licensePlateNumber,
            String manufacturer,
            String model,
            VehicleCategory category,
            Transmission transmission,
            FuelType fuelType,
            Integer year,
            String color,
            Integer seats,
            VehicleLocation location) {
    }
}
