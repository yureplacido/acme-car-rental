package org.acme.rental.repository;

import org.acme.rental.entity.RentalEntity;
import org.acme.rental.model.Rental;
import org.bson.types.ObjectId;

/**
 * Mapeia {@link Rental} (modelo/domínio) ↔ {@link RentalEntity} (persistência Panache
 * MongoDB). Converte um no outro usando o {@code builder} do Lombok; o id modelo é
 * o hex do {@link ObjectId}.
 */
public final class RentalMapper {

    private RentalMapper() {
    }

    public static RentalEntity toEntity(Rental rental) {
        if (rental == null) {
            return null;
        }
        RentalEntity entity = new RentalEntity();
        if (rental.getId() != null) {
            entity.id = new ObjectId(rental.getId());
        }
        entity.userId = rental.getUserId();
        entity.reservationId = rental.getReservationId();
        entity.startDate = rental.getStartDate();
        entity.endDate = rental.getEndDate();
        entity.active = rental.isActive();
        return entity;
    }

    public static Rental toModel(RentalEntity entity) {
        if (entity == null) {
            return null;
        }
        return Rental.builder()
                .id(entity.id != null ? entity.id.toString() : null)
                .userId(entity.userId)
                .reservationId(entity.reservationId)
                .startDate(entity.startDate)
                .endDate(entity.endDate)
                .active(entity.active)
                .build();
    }
}