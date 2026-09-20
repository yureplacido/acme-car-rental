package org.acme.reservation.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.acme.reservation.domain.model.Reservation;

import java.util.List;

@ApplicationScoped
public class ListReservations {

    private final ReservationRepository repository;

    @Inject
    public ListReservations(ReservationRepository repository) {
        this.repository = repository;
    }

    public Uni<List<Reservation>> handle(String customerId) {
        return repository.all()
                .map(items -> items.stream()
                        .filter(r -> customerId == null || customerId.equals(r.customerId().value()))
                        .toList());
    }
}
