package org.acme.reservation.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.acme.reservation.application.query.AvailableVehicle;
import org.acme.reservation.domain.model.RentalPeriod;
import org.acme.reservation.domain.model.Reservation;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@ApplicationScoped
public class FindAvailableVehicles {

    private final InventoryGateway inventoryGateway;
    private final ReservationRepository reservationRepository;

    @Inject
    public FindAvailableVehicles(InventoryGateway inventoryGateway,
                                 ReservationRepository reservationRepository) {
        this.inventoryGateway = inventoryGateway;
        this.reservationRepository = reservationRepository;
    }

    public Uni<List<AvailableVehicle>> handle(LocalDate startDate, LocalDate endDate) {
        RentalPeriod requested = new RentalPeriod(startDate, endDate);
        return Uni.combine().all().unis(
                        inventoryGateway.findVehicles(),
                        reservationRepository.findAll())
                .asTuple()
                .map(tuple -> {
                    Set<Long> reservedVehicleIds = tuple.getItem2().stream()
                            .filter(Reservation::active)
                            .filter(r -> r.period().overlaps(requested))
                            .map(r -> r.vehicleId().value())
                            .collect(Collectors.toSet());

                    return tuple.getItem1().stream()
                            .filter(vehicle -> !reservedVehicleIds.contains(vehicle.id()))
                            .toList();
                });
    }
}
