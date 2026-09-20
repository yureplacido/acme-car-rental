package org.acme.reservation.repository;

import org.acme.reservation.entity.ReservationEntity;
import org.acme.reservation.model.Reservation;

/**
 * Mapeia {@link Reservation} (modelo/domínio) ↔ {@link ReservationEntity} (persistência
 * Panache reativa). Converte um no outro usando o {@code builder} do Lombok.
 */
public final class ReservationMapper {

    private ReservationMapper() {
    }

    public static ReservationEntity toEntity(Reservation reservation) {
        ReservationEntity entity = new ReservationEntity();
        entity.id = reservation.getId();
        entity.carId = reservation.getCarId();
        entity.startDay = reservation.getStartDay();
        entity.endDay = reservation.getEndDay();
        entity.userId = reservation.getUserId();
        return entity;
    }

    public static Reservation toModel(ReservationEntity entity) {
        return Reservation.builder()
                .id(entity.id)
                .carId(entity.carId)
                .startDay(entity.startDay)
                .endDay(entity.endDay)
                .userId(entity.userId)
                .build();
    }
}