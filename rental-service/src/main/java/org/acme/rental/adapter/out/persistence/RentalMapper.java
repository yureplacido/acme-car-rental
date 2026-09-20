package org.acme.rental.adapter.out.persistence;

import org.acme.rental.domain.model.CustomerId;
import org.acme.rental.domain.model.Rental;
import org.acme.rental.domain.model.RentalId;
import org.acme.rental.domain.model.RentalStatus;
import org.acme.rental.domain.model.ReservationId;

public final class RentalMapper {

    private RentalMapper() {
    }

    public static Rental toDomain(RentalEntity entity) {
        return Rental.rehydrate(
                new RentalId(entity.id == null ? null : entity.id.toString()),
                new CustomerId(entity.customerId),
                new ReservationId(entity.reservationId),
                entity.startDate,
                entity.endDate,
                entity.status == null ? RentalStatus.ACTIVE : RentalStatus.valueOf(entity.status));
    }

    public static RentalEntity toEntity(Rental rental) {
        RentalEntity entity = new RentalEntity();
        if (rental.id() != null) {
            entity.id = new org.bson.types.ObjectId(rental.id().value());
        }
        entity.customerId = rental.customerId().value();
        entity.reservationId = rental.reservationId().value();
        entity.startDate = rental.startDate();
        entity.endDate = rental.endDate();
        entity.status = rental.status().name();
        return entity;
    }
}
