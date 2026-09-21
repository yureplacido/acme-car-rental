package org.acme.inventory.application;

import io.smallrye.mutiny.Uni;
import org.acme.inventory.application.port.out.EventPublisher;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

class RegisterVehicleEventTest {

    @Test
    void shouldPublishVehicleRegisteredAfterVehicleIsPersisted() {
        FakeVehicleRepository repository = new FakeVehicleRepository();
        RecordingEventPublisher publisher = new RecordingEventPublisher();
        RegisterVehicle useCase = new RegisterVehicle(repository, publisher);

        Vehicle vehicle = useCase.handle(new RegisterVehicle.Command(
                "abc123", "Ford", "Mustang",
                VehicleCategory.SUV, null, FuelType.GASOLINE,
                2025, "black", 5, null,
                new BigDecimal("149.90"), "BRL"))
                .await().indefinitely();

        assertEquals(1, publisher.events.size());

        VehicleRegistered event = publisher.events.getFirst();
        assertNotNull(event.eventId());
        assertEquals(1, event.version());
        assertNotNull(event.occurredAt());
        assertEquals(vehicle.id(), event.vehicleId());
        assertEquals("ABC123", event.licensePlate());
    }

    static class RecordingEventPublisher implements EventPublisher {
        final List<VehicleRegistered> events = new ArrayList<>();

        @Override
        public Uni<Void> publish(VehicleRegistered event) {
            events.add(event);
            return Uni.createFrom().voidItem();
        }
    }

    static class FakeVehicleRepository implements VehicleRepository {
        private final List<Vehicle> saved = new ArrayList<>();

        @Override
        public Uni<List<Vehicle>> all() {
            return Uni.createFrom().item(List.copyOf(saved));
        }

        @Override
        public Uni<Optional<Vehicle>> findByLicensePlate(
                org.acme.inventory.domain.model.LicensePlate plate) {
            return Uni.createFrom().item(saved.stream()
                    .filter(v -> v.licensePlate().equals(plate))
                    .findFirst());
        }

        @Override
        public Uni<Vehicle> save(Vehicle vehicle) {
            Vehicle persisted = Vehicle.rehydrate(
                    new VehicleId(1L),
                    vehicle.licensePlate(),
                    vehicle.specifications(),
                    vehicle.location(),
                    vehicle.status(),
                    vehicle.dailyRate(),
                    vehicle.odometer(),
                    vehicle.condition());

            saved.add(persisted);
            return Uni.createFrom().item(persisted);
        }
    }
}
