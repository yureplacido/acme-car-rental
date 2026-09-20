package org.acme.inventory.application;

import io.smallrye.mutiny.Uni;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.DecommissionVehicle;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleSpecifications;
import org.acme.inventory.domain.model.VehicleStatus;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecommissionVehicleTest {

    @Test
    void shouldComposeAsyncFindAndSaveOperations() {
        Vehicle vehicle = Vehicle.rehydrate(
                new org.acme.inventory.domain.model.VehicleId(1L),
                new LicensePlate("ABC123"),
                new VehicleSpecifications(
                        "Ford", "Mustang", VehicleCategory.SUV,
                        Transmission.MANUAL, null, 2025, "black", 4),
                null,
                VehicleStatus.AVAILABLE,
                null,
                null,
                null);

        AsyncFakeRepository repository = new AsyncFakeRepository(vehicle);

        Optional<Vehicle> result = new DecommissionVehicle(repository)
                .handle("ABC123")
                .await().indefinitely();

        assertTrue(result.isPresent());
        assertEquals(VehicleStatus.DECOMMISSIONED, result.orElseThrow().status());
        assertEquals(1, repository.findCalls.get());
        assertEquals(1, repository.saveCalls.get());
    }

    static class AsyncFakeRepository implements VehicleRepository {
        private final Vehicle vehicle;
        final AtomicInteger findCalls = new AtomicInteger();
        final AtomicInteger saveCalls = new AtomicInteger();

        AsyncFakeRepository(Vehicle vehicle) {
            this.vehicle = vehicle;
        }

        @Override
        public Uni<List<Vehicle>> all() {
            return Uni.createFrom().item(List.of(vehicle));
        }

        @Override
        public Uni<Optional<Vehicle>> findByLicensePlate(LicensePlate plate) {
            return Uni.createFrom().item(
                            plate.equals(vehicle.licensePlate())
                                    ? Optional.of(vehicle)
                                    : Optional.<Vehicle>empty())
                    .onItem().delayIt().by(Duration.ofMillis(5))
                    .invoke(ignored -> findCalls.incrementAndGet());
        }

        @Override
        public Uni<Vehicle> save(Vehicle value) {
            return Uni.createFrom().item(value)
                    .onItem().delayIt().by(Duration.ofMillis(5))
                    .invoke(ignored -> saveCalls.incrementAndGet());
        }
    }
}
