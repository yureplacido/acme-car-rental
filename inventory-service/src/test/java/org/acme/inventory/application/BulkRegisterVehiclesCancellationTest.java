package org.acme.inventory.application;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.AssertSubscriber;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.BulkRegisterVehicles;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleId;
import org.acme.inventory.domain.model.Transmission;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class BulkRegisterVehiclesCancellationTest {

    @Test
    void shouldPropagateCancellationToActiveOperationsAndStopNewOnes() {
        CancellationAwareVehicleRepository repository =
                new CancellationAwareVehicleRepository();
        RegisterVehicle registerVehicle = new RegisterVehicle(repository);
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(registerVehicle, 2);

        Multi<RegisterVehicle.Command> commands = Multi.createFrom().items(
                command("AAA111"),
                command("BBB222"),
                command("CCC333"),
                command("DDD444"));

        AssertSubscriber<Vehicle> subscriber = useCase.handle(commands)
                .subscribe()
                .withSubscriber(AssertSubscriber.create(0));

        subscriber.awaitSubscription();

        // merge(2) subscribes to at most two inner Uni instances at a time.
        assertEquals(2, repository.started.get());
        subscriber.assertHasNotReceivedAnyItem();

        subscriber.cancel();

        // Cancellation travels from the downstream subscriber to the active
        // inner subscriptions, and no third operation is started afterwards.
        assertEquals(2, repository.cancelled.get());
        assertEquals(2, repository.started.get());
        subscriber.assertHasNotReceivedAnyItem();
    }

    private static RegisterVehicle.Command command(String plate) {
        return new RegisterVehicle.Command(
                plate,
                "Ford",
                "Mustang",
                VehicleCategory.SUV,
                Transmission.AUTOMATIC,
                null,
                2025,
                "black",
                5,
                null,
                null,
                "BRL");
    }

    static class CancellationAwareVehicleRepository implements VehicleRepository {

        private final AtomicLong ids = new AtomicLong();
        private final AtomicInteger started = new AtomicInteger();
        private final AtomicInteger cancelled = new AtomicInteger();

        @Override
        public Uni<List<Vehicle>> all() {
            return Uni.createFrom().item(List.of());
        }

        @Override
        public Uni<Optional<Vehicle>> findByLicensePlate(LicensePlate plate) {
            return Uni.createFrom().item(Optional.empty());
        }

        @Override
        public Uni<Vehicle> save(Vehicle vehicle) {
            Vehicle persisted = Vehicle.rehydrate(
                    new VehicleId(ids.incrementAndGet()),
                    vehicle.licensePlate(),
                    vehicle.specifications(),
                    vehicle.location(),
                    vehicle.status(),
                    vehicle.dailyRate(),
                    vehicle.odometer(),
                    vehicle.condition());

            return Uni.createFrom()
                    .item(persisted)
                    .invoke(ignored -> started.incrementAndGet())
                    .onItem().delayIt().by(Duration.ofHours(1))
                    .onCancellation().invoke(cancelled::incrementAndGet);
        }
    }
}
