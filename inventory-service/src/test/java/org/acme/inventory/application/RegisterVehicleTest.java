package org.acme.inventory.application;

import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RegisterVehicleTest {

    @Test
    void shouldRegisterVehicleThroughRepositoryPort() {
        FakeVehicleRepository repository = new FakeVehicleRepository();
        RegisterVehicle useCase = new RegisterVehicle(repository);

        Vehicle vehicle = useCase.handle(new RegisterVehicle.Command(
                "abc123", "Ford", "Mustang",
                VehicleCategory.SUV, null, null,
                2025, "black", 5, null, new BigDecimal("149.90"), "BRL"));

        assertEquals("ABC123", vehicle.licensePlate().value());
        assertEquals(1, repository.saved.size());
        assertEquals("Ford", repository.saved.getFirst().specifications().manufacturer());
    }

    static class FakeVehicleRepository implements VehicleRepository {
        final List<Vehicle> saved = new ArrayList<>();

        public List<Vehicle> findAll() { return List.copyOf(saved); }
        public java.util.Optional<Vehicle> findByLicensePlate(org.acme.inventory.domain.model.LicensePlate plate) {
            return saved.stream().filter(v -> v.licensePlate().equals(plate)).findFirst();
        }
        public Vehicle save(Vehicle vehicle) {
            Vehicle persisted = Vehicle.rehydrate(
                    new org.acme.inventory.domain.model.VehicleId(1L),
                    vehicle.licensePlate(),
                    vehicle.specifications(),
                    vehicle.location(),
                    vehicle.status(),
                    vehicle.dailyRate());
            saved.add(persisted);
            return persisted;
        }
    }
}
