package org.acme.reservation.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.RentalGateway;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.acme.reservation.domain.model.CustomerId;
import org.acme.reservation.domain.model.RentalPeriod;
import org.acme.reservation.domain.model.Reservation;
import org.acme.reservation.domain.model.VehicleId;

import java.time.LocalDate;

@ApplicationScoped
public class CreateReservation {

    private final ReservationRepository repository;
    private final RentalGateway rentalGateway;

    @Inject
    public CreateReservation(ReservationRepository repository,
                              RentalGateway rentalGateway) {
        this.repository = repository;
        this.rentalGateway = rentalGateway;
    }

    public Uni<Reservation> handle(Command command) {
        Reservation reservation = Reservation.create(
                new CustomerId(command.customerId()),
                new VehicleId(command.vehicleId()),
                new RentalPeriod(command.startDate(), command.endDate()));

        return repository.findByVehicle(reservation.vehicleId())
                .chain(existing -> {
                    boolean conflict = existing.stream().anyMatch(reservation::conflictsWith);
                    if (conflict) {
                        return Uni.createFrom().failure(
                                new IllegalStateException("vehicle is already reserved for the requested period"));
                    }
                    return repository.save(reservation);
                })
                .call(persisted -> {
                    if (command.today().equals(persisted.period().start())) {
                        return rentalGateway.start(
                                persisted.customerId().value(),
                                persisted.id().value());
                    }
                    return Uni.createFrom().voidItem();
                });
    }

    public record Command(
            String customerId,
            Long vehicleId,
            LocalDate startDate,
            LocalDate endDate,
            LocalDate today) {
    }
}
