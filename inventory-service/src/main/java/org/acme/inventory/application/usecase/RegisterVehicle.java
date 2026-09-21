package org.acme.inventory.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.inventory.application.port.out.EventPublisher;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleDailyRate;
import org.acme.inventory.domain.model.VehicleLocation;
import org.acme.inventory.domain.model.VehicleSpecifications;

import java.math.BigDecimal;

@ApplicationScoped
public class RegisterVehicle {

    private final VehicleRepository repository;
    private final EventPublisher eventPublisher;

    @Inject
    public RegisterVehicle(VehicleRepository repository, EventPublisher eventPublisher) {
        this.repository = repository;
        this.eventPublisher = eventPublisher;
    }

    public RegisterVehicle(VehicleRepository repository) {
        this(repository, event -> Uni.createFrom().voidItem());
    }

    public Uni<Vehicle> handle(Command command) {
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
                command.location(),
                command.dailyRate() == null
                        ? null
                        : new VehicleDailyRate(
                        command.dailyRate(),
                        command.currency() == null || command.currency().isBlank() ? "BRL" : command.currency()));

        return repository.save(vehicle)
                .call(savedVehicle -> eventPublisher.publish(
                        VehicleRegistered.from(
                                savedVehicle.id(),
                                savedVehicle.licensePlate().value())));
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
            VehicleLocation location,
            BigDecimal dailyRate,
            String currency) {
    }
}
