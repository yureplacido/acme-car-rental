package org.acme.rental.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.rental.application.port.out.RentalRepository;
import org.acme.rental.domain.model.CustomerId;
import org.acme.rental.domain.model.Rental;
import org.acme.rental.domain.model.ReservationId;

import java.time.LocalDate;

@ApplicationScoped
public class StartRental {

    private final RentalRepository repository;

    @Inject
    public StartRental(RentalRepository repository) {
        this.repository = repository;
    }

    public Rental handle(Command command) {
        return repository.save(Rental.start(
                new CustomerId(command.customerId()),
                new ReservationId(command.reservationId()),
                command.today()));
    }

    public record Command(String customerId, Long reservationId, LocalDate today) {
    }
}
