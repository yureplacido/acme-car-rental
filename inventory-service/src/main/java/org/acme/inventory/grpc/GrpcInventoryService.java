package org.acme.inventory.grpc;

import io.quarkus.grpc.GrpcService;
import io.quarkus.logging.Log;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.acme.inventory.domain.CarInventoryService;
import org.acme.inventory.model.graphql.Car;
import org.acme.inventory.model.CarResponse;
import org.acme.inventory.model.graphql.Category;
import org.acme.inventory.model.graphql.FuelType;
import org.acme.inventory.model.InsertCarRequest;
import org.acme.inventory.model.InventoryService;
import org.acme.inventory.model.RemoveCarRequest;
import org.acme.inventory.model.graphql.Transmission;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Adapter gRPC — canal máquina-a-máquina (admin/operação/bulk). Só traduz o wire
 * para o domínio; toda regra de negócio vive em {@link CarInventoryService}.
 */
@GrpcService
public class GrpcInventoryService implements InventoryService {

    @Inject
    CarInventoryService carInventory;

    @Blocking
    @Override
    public Multi<CarResponse> add(Multi<InsertCarRequest> requests) {
        return requests
                .map(this::toCar)
                .onItem().invoke(car -> Log.infof("Persisting %s", car))
                // register() devolve um NOVO model com o id preenchido (o input não é mutado).
                .onItem().transform(car -> {
                    Car persisted = carInventory.register(car);
                    Log.infof("Inserted new car %s", persisted);
                    return persisted;
                })
                .map(this::toResponse);
    }

    @Blocking
    @Override
    public Uni<CarResponse> remove(RemoveCarRequest request) {
        Optional<Car> car = carInventory.decommission(request.getLicensePlateNumber());
        return car.isPresent()
                ? Uni.createFrom().item(toResponse(car.get()))
                : Uni.createFrom().nullItem();
    }

    private Car toCar(InsertCarRequest request) {
        return Car.builder()
                .licensePlateNumber(request.getLicensePlateNumber())
                .manufacturer(request.getManufacturer())
                .model(request.getModel())
                .color(emptyToNull(request.getColor()))
                .year(request.getYear() == 0 ? null : request.getYear())
                .category(parseEnum(Category.class, request.getCategory()))
                .transmission(parseEnum(Transmission.class, request.getTransmission()))
                .fuelType(parseEnum(FuelType.class, request.getFuelType()))
                .seats(request.getSeats() == 0 ? null : request.getSeats())
                .dailyRate(request.getDailyRate() == 0 ? null : BigDecimal.valueOf(request.getDailyRate()))
                .build();
    }

    private CarResponse toResponse(Car car) {
        CarResponse.Builder builder = CarResponse.newBuilder()
                .setLicensePlateNumber(car.getLicensePlateNumber())
                .setManufacturer(car.getManufacturer())
                .setModel(car.getModel())
                .setId(car.getId())
                .setStatus(car.getStatus() == null ? "" : car.getStatus().name())
                .setColor(valueOrEmpty(car.getColor()))
                .setYear(car.getYear() == null ? 0 : car.getYear())
                .setCategory(valueOrEmpty(car.getCategory()))
                .setTransmission(valueOrEmpty(car.getTransmission()))
                .setFuelType(valueOrEmpty(car.getFuelType()))
                .setSeats(car.getSeats() == null ? 0 : car.getSeats());
        if (car.getDailyRate() != null) {
            builder.setDailyRate(car.getDailyRate().doubleValue());
        }
        return builder.build();
    }

    private static String emptyToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private static String valueOrEmpty(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Enum.valueOf(type, value.toUpperCase());
        } catch (IllegalArgumentException e) {
            Log.warnf("Valor inválido para %s: '%s'; ignorado", type.getSimpleName(), value);
            return null;
        }
    }
}