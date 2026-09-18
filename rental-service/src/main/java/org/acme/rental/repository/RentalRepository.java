package org.acme.rental.repository;

import org.acme.rental.model.Rental;

import java.util.List;

public interface RentalRepository {

    List<Rental> findAll();

    Rental save(Rental rental);
}