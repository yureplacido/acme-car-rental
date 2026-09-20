package org.acme.inventory.application;

import io.smallrye.mutiny.Uni;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.FindVehicleByPlate;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class FindVehicleByPlateTest {

    @Test
    void shouldFindVehicleByNormalizedPlate() {
        Vehicle vehicle = Vehicle.register(
                new LicensePlate("abc123"),
                new org.acme.inventory.domain.model.VehicleSpecifications(
                        "Ford", "Mustang", null,
                        org.acme.inventory.domain.model.Transmission.MANUAL,
                        null, 2025, null, 4),
                null,
                null);

        VehicleRepository repository = new FakeRepository(vehicle);

        Vehicle result = new FindVehicleByPlate(repository)
                .handle(" ABC123 ")
                .await().indefinitely()
                .orElseThrow();

        assertEquals("ABC123", result.licensePlate().value());
    }

    static class FakeRepository implements VehicleRepository {
        private final Vehicle vehicle;

        FakeRepository(Vehicle vehicle) {
            this.vehicle = vehicle;
        }

        public Uni<List<Vehicle>> all() { return Uni.createFrom().item(List.of(vehicle)); }
        public Uni<Optional<Vehicle>> findByLicensePlate(LicensePlate plate) {
            return Uni.createFrom().item(
                    plate.equals(vehicle.licensePlate()) ? Optional.of(vehicle) : Optional.empty());
        }
        public Uni<Vehicle> save(Vehicle value) { return Uni.createFrom().item(value); }
    }
}
