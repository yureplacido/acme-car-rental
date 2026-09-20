package org.acme.rental.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.rental.application.port.out.RentalRepository;
import org.acme.rental.domain.model.Rental;

import java.util.List;

@ApplicationScoped
public class ListRentals {

    private final RentalRepository repository;

    @Inject
    public ListRentals(RentalRepository repository) {
        this.repository = repository;
    }

    public List<Rental> handle(boolean activeOnly) {
        return activeOnly ? repository.findActive() : repository.findAll();
    }
}
