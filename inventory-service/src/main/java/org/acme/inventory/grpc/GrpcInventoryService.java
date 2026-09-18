package org.acme.inventory.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
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

    @Override
    public Uni<CarResponse> add(InsertCarRequest request) {
        Car car = Car.builder()
                .manufacturer(request.getManufacturer())
                .model(request.getModel())
                .licensePlateNumber(request.getLicensePlateNumber())
                .build();
        Car saved = carRepository.save(car);
        return Uni.createFrom().item(toResponse(saved));
    }

    @Override
    public Uni<CarResponse> remove(RemoveCarRequest request) {
        Optional<Car> optionalCar = carRepository.deleteByPlate(request.getLicensePlateNumber());

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