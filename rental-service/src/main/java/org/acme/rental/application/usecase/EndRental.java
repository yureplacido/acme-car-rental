package org.acme.rental.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.rental.application.port.out.RentalRepository;
import org.acme.rental.domain.model.Rental;

import java.time.LocalDate;

@ApplicationScoped
public class EndRental {

    private final RentalRepository repository;

    @Inject
    public EndRental(RentalRepository repository) {
        this.repository = repository;
    }

    public Rental handle(Command command) {
        Rental rental = repository.findByCustomerAndReservation(
                        command.customerId(), command.reservationId())
                .orElseThrow(() -> new IllegalArgumentException("rental not found"));
        return repository.save(rental.finish(command.today()));
    }

    public record Command(String customerId, Long reservationId, LocalDate today) {
    }
}
