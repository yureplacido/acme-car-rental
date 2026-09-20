package org.acme.inventory.repository;

import org.acme.inventory.entity.CarEntity;
import org.acme.inventory.model.Car;

/**
 * Mapeia {@link Car} (modelo/domínio) ↔ {@link CarEntity} (persistência Panache).
 * Idempotente: converte um no outro usando o {@code builder} do Lombok.
 */
public final class CarMapper {

    private CarMapper() {
    }

    public static CarEntity toEntity(Car car) {
        CarEntity entity = new CarEntity();
        entity.id = car.getId();
        entity.manufacturer = car.getManufacturer();
        entity.model = car.getModel();
        entity.licensePlateNumber = car.getLicensePlateNumber();
        return entity;
    }

    public static Car toModel(CarEntity entity) {
        return Car.builder()
                .id(entity.id)
                .manufacturer(entity.manufacturer)
                .model(entity.model)
                .licensePlateNumber(entity.licensePlateNumber)
                .build();
    }
}