package org.acme.inventory.adapter.in.grpc;

import io.quarkus.grpc.GrpcService;
import io.smallrye.common.annotation.Blocking;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.acme.inventory.application.usecase.DecommissionVehicle;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.model.CarResponse;
import org.acme.inventory.model.InsertCarRequest;
import org.acme.inventory.model.InventoryService;
import org.acme.inventory.model.RemoveCarRequest;
import org.acme.inventory.domain.model.VehicleLocation;

import java.math.BigDecimal;
import java.util.Optional;

@GrpcService
public class GrpcInventoryResource implements InventoryService {

    private final RegisterVehicle registerVehicle;
    private final DecommissionVehicle decommissionVehicle;

    @Inject
    public GrpcInventoryResource(RegisterVehicle registerVehicle,
                                 DecommissionVehicle decommissionVehicle) {
        this.registerVehicle = registerVehicle;
        this.decommissionVehicle = decommissionVehicle;
    }

    @Blocking
    @Override
    public Multi<CarResponse> add(Multi<InsertCarRequest> requests) {
        return requests
                .map(this::toCommand)
                .map(registerVehicle::handle)
                .map(this::toResponse);
    }

    @Blocking
    @Override
    public Uni<CarResponse> remove(RemoveCarRequest request) {
        Optional<Vehicle> vehicle = decommissionVehicle.handle(request.getLicensePlateNumber());
        return vehicle.map(v -> Uni.createFrom().item(toResponse(v)))
                .orElseGet(() -> Uni.createFrom().nullItem());
    }

    private RegisterVehicle.Command toCommand(InsertCarRequest request) {
        return new RegisterVehicle.Command(
                request.getLicensePlateNumber(),
                request.getManufacturer(),
                request.getModel(),
                parseEnum(VehicleCategory.class, request.getCategory()),
                parseEnum(Transmission.class, request.getTransmission()),
                parseEnum(FuelType.class, request.getFuelType()),
                request.getYear() == 0 ? null : request.getYear(),
                emptyToNull(request.getColor()),
                request.getSeats() == 0 ? null : request.getSeats(),
                null);
    }

    private CarResponse toResponse(Vehicle vehicle) {
        CarResponse.Builder builder = CarResponse.newBuilder()
                .setLicensePlateNumber(vehicle.licensePlate().value())
                .setManufacturer(vehicle.specifications().manufacturer())
                .setModel(vehicle.specifications().model())
                .setId(vehicle.id().value())
                .setStatus(vehicle.status().name())
                .setColor(valueOrEmpty(vehicle.specifications().color()))
                .setYear(vehicle.specifications().year() == null ? 0 : vehicle.specifications().year())
                .setCategory(valueOrEmpty(vehicle.specifications().category()))
                .setTransmission(valueOrEmpty(vehicle.specifications().transmission()))
                .setFuelType(valueOrEmpty(vehicle.specifications().fuelType()))
                .setSeats(vehicle.specifications().seats() == null ? 0 : vehicle.specifications().seats());
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
        return Enum.valueOf(type, value.trim().toUpperCase());
    }
}
