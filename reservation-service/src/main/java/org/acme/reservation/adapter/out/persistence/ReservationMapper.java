package org.acme.reservation.adapter.out.persistence;

import org.acme.reservation.domain.model.CustomerId;
import org.acme.reservation.domain.model.RentalPeriod;
import org.acme.reservation.domain.model.Reservation;
import org.acme.reservation.domain.model.ReservationId;
import org.acme.reservation.domain.model.VehicleId;

public final class ReservationMapper {

    private ReservationMapper() {
    }

    public static Reservation toDomain(ReservationEntity entity) {
        return Reservation.rehydrate(
                new ReservationId(entity.id),
                new CustomerId(entity.userId == null ? "anonymous" : entity.userId),
                new VehicleId(entity.carId),
                new RentalPeriod(entity.startDay, entity.endDay),
                entity.status);
    }

    public static ReservationEntity toEntity(Reservation reservation) {
        ReservationEntity entity = new ReservationEntity();
        entity.id = reservation.id() == null ? null : reservation.id().value();
        entity.carId = reservation.vehicleId().value();
        entity.userId = reservation.customerId().value();
        entity.startDay = reservation.period().start();
        entity.endDay = reservation.period().end();
        entity.status = reservation.status();
        return entity;
    }
}
