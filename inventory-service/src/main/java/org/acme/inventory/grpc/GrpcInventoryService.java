package org.acme.inventory.grpc;

import io.quarkus.grpc.GrpcService;
import io.quarkus.logging.Log;
import io.quarkus.narayana.jta.QuarkusTransaction;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.acme.inventory.model.Car;
import org.acme.inventory.model.CarResponse;
import org.acme.inventory.model.InsertCarRequest;
import org.acme.inventory.model.InventoryService;
import org.acme.inventory.model.RemoveCarRequest;
import org.acme.inventory.repository.CarRepository;

import java.util.Optional;

@GrpcService
public class GrpcInventoryService implements InventoryService {

    @Inject
    CarRepository carRepository;

    @Blocking
    @Override
    public Multi<CarResponse> add(Multi<InsertCarRequest> requests) {
        return requests
                .map(request -> Car.builder()
                        .manufacturer(request.getManufacturer())
                        .model(request.getModel())
                        .licensePlateNumber(request.getLicensePlateNumber())
                        .build())
                .onItem().invoke(car -> Log.infof("Persisting %s", car))
                // save() devolve um NOVO model com o id preenchido (o input não é mutado).
                .onItem().transform(car -> {
                    Car persisted = QuarkusTransaction.requiringNew().call(() -> carRepository.save(car));
                    Log.infof("Inserted new car %s", persisted);
                    return persisted;
                })
                .map(this::toResponse);
    }

    @Blocking
    @Transactional
    @Override
    public Uni<CarResponse> remove(RemoveCarRequest request) {
        Optional<Car> optionalCar = carRepository.findByLicensePlateNumberOptional(request.getLicensePlateNumber());

        if (optionalCar.isPresent()) {
            return Uni.createFrom().item(toResponse(optionalCar.get()));
        }
        return Uni.createFrom().nullItem();
    }

    private CarResponse toResponse(Car car) {
        return CarResponse.newBuilder()
                .setLicensePlateNumber(car.getLicensePlateNumber())
                .setManufacturer(car.getManufacturer())
                .setModel(car.getModel())
                .setId(car.getId())
                .build();
    }
}