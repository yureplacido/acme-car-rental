package org.acme.inventory.repository;

import org.acme.inventory.model.Car;

import java.util.List;
import java.util.Optional;

public interface CarRepository {

    List<Car> findAll();

    Optional<Car> findByPlate(String licensePlateNumber);

    Car save(Car car);

    Optional<Car> deleteByPlate(String licensePlateNumber);
}