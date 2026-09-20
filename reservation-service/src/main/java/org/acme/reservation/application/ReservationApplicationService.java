package org.acme.reservation.application;

import io.smallrye.mutiny.Uni;
import org.acme.reservation.client.rental.RentalClient;
import org.acme.reservation.model.Reservation;
import org.acme.reservation.repository.ReservationRepository;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;

@ApplicationScoped
public class ReservationApplicationService {

    private final ReservationRepository repository;
    private final RentalClient rentalClient;

    public ReservationApplicationService(ReservationRepository repository,
                                          RentalClient rentalClient) {
        this.repository = repository;
        this.rentalClient = rentalClient;
    }

    public Uni<Reservation> create(Reservation reservation, String userId) {
        reservation.setUserId(userId == null ? "anonymous" : userId);

        return repository.save(reservation)
                .onItem().call(persisted -> {
                    if (LocalDate.now().equals(persisted.getStartDay())) {
                        return rentalClient.start(
                                        persisted.getUserId(),
                                        persisted.getId())
                                .replaceWith(persisted);
                    }
                    return Uni.createFrom().item(persisted);
                });
    }
}
