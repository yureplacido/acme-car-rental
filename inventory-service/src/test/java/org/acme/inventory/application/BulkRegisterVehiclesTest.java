package org.acme.inventory.application;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.Multi;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.BulkRegisterVehicles;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkRegisterVehiclesTest {

    @Test
    void shouldRegisterAllCommandsFromStream() {
        FakeVehicleRepository repository = new FakeVehicleRepository();
        RegisterVehicle registerVehicle = new RegisterVehicle(repository);
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(registerVehicle, 2);

        List<String> plates = useCase.handle(
                        Multi.createFrom().items(
                                command("AAA111"),
                                command("BBB222"),
                                command("CCC333"),
                                command("DDD444")))
                .map(vehicle -> vehicle.licensePlate().value())
                .collect().asList()
                .await().indefinitely();

        assertEquals(
                Set.of("AAA111", "BBB222", "CCC333", "DDD444"),
                Set.copyOf(plates));
        assertEquals(4, repository.saved.size());
    }

    @Test
    void shouldNeverExceedConfiguredConcurrency() {
        FakeVehicleRepository repository = new FakeVehicleRepository();
        RegisterVehicle registerVehicle = new RegisterVehicle(repository);
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(registerVehicle, 2);

        List<RegisterVehicle.Command> commands = List.of(
                command("AAA111"),
                command("BBB222"),
                command("CCC333"),
                command("DDD444"),
                command("EEE555"),
                command("FFF666"));

        List<Vehicle> result = useCase.handle(Multi.createFrom().iterable(commands))
                .collect().asList()
                .await().indefinitely();

        assertEquals(6, result.size());
        assertTrue(repository.maxInFlight.get() <= 2);
        assertEquals(2, repository.maxInFlight.get());
    }

    private RegisterVehicle.Command command(String plate) {
        return new RegisterVehicle.Command(
                plate,
                "Ford",
                "Mustang",
                VehicleCategory.SUV,
                null,
                null,
                2025,
                "black",
                5,
                null,
                null,
                "BRL");
    }

    static class FakeVehicleRepository implements VehicleRepository {
        final List<Vehicle> saved = new ArrayList<>();
        final AtomicLong ids = new AtomicLong();
        final AtomicInteger inFlight = new AtomicInteger();
        final AtomicInteger maxInFlight = new AtomicInteger();

        public Uni<List<Vehicle>> all() {
            return Uni.createFrom().item(List.copyOf(saved));
        }

        public Uni<Optional<Vehicle>> findByLicensePlate(LicensePlate plate) {
            return Uni.createFrom().item(
                    saved.stream()
                            .filter(v -> v.licensePlate().equals(plate))
                            .findFirst());
        }

        public Uni<Vehicle> save(Vehicle vehicle) {
            return Uni.createFrom().deferred(() -> {
                Vehicle persisted = Vehicle.rehydrate(
                        new VehicleId(ids.incrementAndGet()),
                        vehicle.licensePlate(),
                        vehicle.specifications(),
                        vehicle.location(),
                        vehicle.status(),
                        vehicle.dailyRate(),
                        vehicle.odometer(),
                        vehicle.condition());

                saved.add(persisted);

                int current = inFlight.incrementAndGet();
                maxInFlight.accumulateAndGet(current, Math::max);

                return Uni.createFrom().item(persisted)
                        .onItem().delayIt().by(Duration.ofMillis(20))
                        .onTermination().invoke(inFlight::decrementAndGet);
            });
        }
    }
}
