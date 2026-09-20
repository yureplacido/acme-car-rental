package org.acme.rental.application.port.out;

import org.acme.rental.domain.model.Rental;

import java.util.List;
import java.util.Optional;

public interface RentalRepository {
    Rental save(Rental rental);
    Optional<Rental> findByCustomerAndReservation(String customerId, Long reservationId);
    List<Rental> findAll();
    List<Rental> findActive();
}
