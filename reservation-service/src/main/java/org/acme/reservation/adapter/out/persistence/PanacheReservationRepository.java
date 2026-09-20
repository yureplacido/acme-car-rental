package org.acme.reservation.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.acme.reservation.domain.model.RentalPeriod;
import org.acme.reservation.domain.model.Reservation;
import org.acme.reservation.domain.model.VehicleId;

import java.util.List;

@ApplicationScoped
@WithSession
public class PanacheReservationRepository implements ReservationRepository, PanacheRepository<ReservationEntity> {

    @Override
    public Uni<List<Reservation>> findAll() {
        return listAll().map(items -> items.stream()
                .map(ReservationMapper::toDomain)
                .toList());
    }

    @Override
    public Uni<Reservation> save(Reservation reservation) {
        ReservationEntity entity = ReservationMapper.toEntity(reservation);
        return persist(entity).map(ReservationMapper::toDomain);
    }

    @Override
    public Uni<Boolean> hasOverlap(VehicleId vehicleId, Reservation reservation) {
        RentalPeriod period = reservation.period();
        return find("carId = ?1", vehicleId.value())
                .list()
                .map(items -> items.stream()
                        .map(ReservationMapper::toDomain)
                        .filter(Reservation::active)
                        .anyMatch(existing -> existing.period().overlaps(period)));
    }
}
