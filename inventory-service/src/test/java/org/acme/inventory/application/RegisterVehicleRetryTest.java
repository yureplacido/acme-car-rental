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

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RegisterVehicleRetryTest {

    @Test
    void shouldRetryTransientFailureAndSucceed() {
        FlakyVehicleRepository repository = new FlakyVehicleRepository(2);
        RegisterVehicle registerVehicle = new RegisterVehicle(repository);

        Vehicle result = registerVehicle.handle(command())
                .onFailure(IOException.class)
                .retry()
                .atMost(2)
                .await()
                .indefinitely();

        assertEquals("AAA111", result.licensePlate().value());
        assertEquals(3, repository.attempts.get());
    }

    @Test
    void shouldStopAfterConfiguredNumberOfRetries() {
        FlakyVehicleRepository repository = new FlakyVehicleRepository(Integer.MAX_VALUE);
        RegisterVehicle registerVehicle = new RegisterVehicle(repository);

        UniAssertSubscriber<Vehicle> subscriber = registerVehicle.handle(command())
                .onFailure(IOException.class)
                .retry()
                .atMost(2)
                .subscribe()
                .withSubscriber(UniAssertSubscriber.create());

        subscriber
                .awaitFailure()
                .assertFailedWith(IOException.class);

        // Initial attempt + 2 retries = 3 subscriptions.
        assertEquals(3, repository.attempts.get());
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

    static class FlakyVehicleRepository implements VehicleRepository {

        private final int failuresBeforeSuccess;
        private final AtomicInteger attempts = new AtomicInteger();
        private final AtomicLong ids = new AtomicLong();

        FlakyVehicleRepository(int failuresBeforeSuccess) {
            this.failuresBeforeSuccess = failuresBeforeSuccess;
        }

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
            return Uni.createFrom().deferred(() -> {
                int attempt = attempts.incrementAndGet();

                if (attempt <= failuresBeforeSuccess) {
                    return Uni.createFrom().failure(
                            new IOException("temporary inventory persistence failure"));
                }

                return Uni.createFrom().item(Vehicle.rehydrate(
                        new VehicleId(ids.incrementAndGet()),
                        vehicle.licensePlate(),
                        vehicle.specifications(),
                        vehicle.location(),
                        vehicle.status(),
                        vehicle.dailyRate(),
                        vehicle.odometer(),
                        vehicle.condition()));
            });
        }
    }
}
