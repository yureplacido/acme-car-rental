package org.acme.inventory.repository;

import org.acme.inventory.entity.CarEntity;
import org.acme.inventory.model.graphql.Car;

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
        entity.status = car.getStatus();
        entity.category = car.getCategory();
        entity.transmission = car.getTransmission();
        entity.fuelType = car.getFuelType();
        entity.year = car.getYear();
        entity.color = car.getColor();
        entity.seats = car.getSeats();
        entity.dailyRate = car.getDailyRate();
        return entity;
    }

    public static Car toModel(CarEntity entity) {
        return Car.builder()
                .id(entity.id)
                .manufacturer(entity.manufacturer)
                .model(entity.model)
                .licensePlateNumber(entity.licensePlateNumber)
                .status(entity.status)
                .category(entity.category)
                .transmission(entity.transmission)
                .fuelType(entity.fuelType)
                .year(entity.year)
                .color(entity.color)
                .seats(entity.seats)
                .dailyRate(entity.dailyRate)
                .build();
    }
}