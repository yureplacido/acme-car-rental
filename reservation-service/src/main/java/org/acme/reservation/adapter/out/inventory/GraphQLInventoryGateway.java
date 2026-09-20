package org.acme.reservation.adapter.out.inventory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.query.AvailableVehicle;
import org.acme.reservation.adapter.out.inventory.model.Car;

import java.util.List;

@ApplicationScoped
public class GraphQLInventoryGateway implements InventoryGateway {

    private final GraphQLInventoryClient client;

    @Inject
    public GraphQLInventoryGateway(GraphQLInventoryClient client) {
        this.client = client;
    }

    @Override
    public Uni<List<AvailableVehicle>> findVehicles() {
        return client.allCars()
                .map(cars -> cars.stream()
                        .map(this::toAvailableVehicle)
                        .toList());
    }

    private AvailableVehicle toAvailableVehicle(Car car) {
        return new AvailableVehicle(
                car.getId(),
                car.getLicensePlateNumber(),
                car.getManufacturer(),
                car.getModel());
    }
}
