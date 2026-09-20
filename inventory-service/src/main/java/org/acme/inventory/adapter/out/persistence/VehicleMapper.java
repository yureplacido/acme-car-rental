package org.acme.inventory.adapter.out.persistence;

import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleId;
import org.acme.inventory.domain.model.VehicleLocation;
import org.acme.inventory.domain.model.VehicleSpecifications;
import org.acme.inventory.domain.model.VehicleStatus;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.Transmission;

public final class VehicleMapper {

    private VehicleMapper() {
    }

    public static Vehicle toDomain(VehicleEntity entity) {
        return Vehicle.rehydrate(
                new VehicleId(entity.id),
                new LicensePlate(entity.licensePlateNumber),
                new VehicleSpecifications(
                        entity.manufacturer,
                        entity.model,
                        entity.category,
                        entity.transmission,
                        entity.fuelType,
                        entity.year,
                        entity.color,
                        entity.seats),
                entity.branchCode == null || entity.city == null
                        ? null
                        : new VehicleLocation(entity.branchCode, entity.city),
                entity.status);
    }

    public static VehicleEntity toEntity(Vehicle vehicle) {
        VehicleEntity entity = new VehicleEntity();
        entity.id = vehicle.id() == null ? null : vehicle.id().value();
        entity.licensePlateNumber = vehicle.licensePlate().value();
        entity.manufacturer = vehicle.specifications().manufacturer();
        entity.model = vehicle.specifications().model();
        entity.category = vehicle.specifications().category();
        entity.transmission = vehicle.specifications().transmission();
        entity.fuelType = vehicle.specifications().fuelType();
        entity.year = vehicle.specifications().year();
        entity.color = vehicle.specifications().color();
        entity.seats = vehicle.specifications().seats();
        entity.status = vehicle.status();
        if (vehicle.location() != null) {
            entity.branchCode = vehicle.location().branchCode();
            entity.city = vehicle.location().city();
        }
        return entity;
    }
}
