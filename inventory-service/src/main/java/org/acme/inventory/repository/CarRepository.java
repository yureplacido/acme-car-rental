package org.acme.inventory.repository;

import org.acme.inventory.model.Car;

import java.util.List;

public interface CarRepository {

    List<Car> findAll();

    long nextId();
}