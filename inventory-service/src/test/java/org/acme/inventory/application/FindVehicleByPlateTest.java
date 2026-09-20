package org.acme.inventory.application;

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
                .orElseThrow();

        assertEquals("ABC123", result.licensePlate().value());
    }

    static class FakeRepository implements VehicleRepository {
        private final Vehicle vehicle;

        FakeRepository(Vehicle vehicle) {
            this.vehicle = vehicle;
        }

        public List<Vehicle> findAll() { return List.of(vehicle); }
        public Optional<Vehicle> findByLicensePlate(LicensePlate plate) {
            return plate.equals(vehicle.licensePlate()) ? Optional.of(vehicle) : Optional.empty();
        }
        public Vehicle save(Vehicle value) { return value; }
    }
}
