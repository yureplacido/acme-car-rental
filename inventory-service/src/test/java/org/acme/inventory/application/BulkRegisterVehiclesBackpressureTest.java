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
import org.acme.inventory.domain.model.VehicleSpecifications;
import org.acme.inventory.domain.model.VehicleStatus;
import org.acme.inventory.domain.model.Transmission;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class BulkRegisterVehiclesBackpressureTest {

    @Test
    void shouldNotEmitWithoutDownstreamDemandAndBoundUpstreamPrefetch() {
        AtomicLong upstreamRequests = new AtomicLong();
        RegisterVehicle registerVehicle = new RegisterVehicle(new ImmediateVehicleRepository());
        BulkRegisterVehicles useCase = new BulkRegisterVehicles(
                registerVehicle,
                2);

        Multi<RegisterVehicle.Command> commands = Multi.createFrom()
                .items(
                        command("AAA111"),
                        command("BBB222"),
                        command("CCC333"))
                .onRequest()
                .invoke(upstreamRequests::addAndGet);

        AssertSubscriber<Vehicle> subscriber = useCase.handle(commands)
                .subscribe()
                .withSubscriber(AssertSubscriber.create(0));

        subscriber.awaitSubscription();

        // merge(maxConcurrency) may prefetch up to its configured concurrency
        // to keep inner subscriptions available, even with zero downstream demand.
        assertEquals(2L, upstreamRequests.get());
        subscriber.assertHasNotReceivedAnyItem();

        subscriber.request(1);
        subscriber.awaitItems(1);

        assertTrue(upstreamRequests.get() >= 2);
        assertEquals(1, subscriber.getItems().size());

        subscriber.cancel();
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

    static class ImmediateVehicleRepository implements VehicleRepository {

        private final AtomicLong ids = new AtomicLong();

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
            return Uni.createFrom().item(Vehicle.rehydrate(
                    new VehicleId(ids.incrementAndGet()),
                    vehicle.licensePlate(),
                    vehicle.specifications(),
                    vehicle.location(),
                    vehicle.status(),
                    vehicle.dailyRate(),
                    vehicle.odometer(),
                    vehicle.condition()));
        }
    }
}
