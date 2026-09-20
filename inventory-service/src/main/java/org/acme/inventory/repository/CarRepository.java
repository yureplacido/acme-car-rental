package org.acme.inventory.repository;

import org.acme.inventory.model.Car;

import java.util.List;
import java.util.Optional;

/**
 * Porta de acesso ao inventário de veículos — fala no {@code model.Car} (domínio),
 * desacoplada da persistência. Implementações concretas podem usar o Active Record
 * ou o Repository pattern sobre {@code CarEntity}, sem tocar no modelo/API.
 */
public interface CarRepository {

    List<Car> all();

    Optional<Car> findByLicensePlateNumberOptional(String licensePlateNumber);

    Car save(Car car);

    boolean deleteByLicensePlateNumber(String licensePlateNumber);
}