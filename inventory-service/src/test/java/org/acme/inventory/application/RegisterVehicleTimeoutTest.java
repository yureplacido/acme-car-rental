package org.acme.inventory.application;

import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.helpers.test.UniAssertSubscriber;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegisterVehicleTimeoutTest {

    @Test
    void shouldFailWhenReactiveOperationExceedsDeadline() {
        SlowVehicleRepository repository = new SlowVehicleRepository();
        RegisterVehicle registerVehicle = new RegisterVehicle(repository);

        Uni<Vehicle> operation = registerVehicle.handle(command())
                .ifNoItem().after(Duration.ofMillis(25))
                .fail();

        UniAssertSubscriber<Vehicle> subscriber = operation
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create());

        subscriber
                .awaitFailure()
                .assertFailedWith(TimeoutException.class);

        assertEquals(1, repository.started.get());
        assertEquals(1, repository.cancelled.get());
    }

    private static RegisterVehicle.Command command() {
        return new RegisterVehicle.Command(
                "AAA111",
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

    static class SlowVehicleRepository implements VehicleRepository {

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
                    .onItem().delayIt().by(Duration.ofSeconds(1))
                    .onCancellation().invoke(cancelled::incrementAndGet);
        }
    }
}
