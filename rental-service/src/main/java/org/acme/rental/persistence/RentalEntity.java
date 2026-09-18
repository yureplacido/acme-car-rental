package org.acme.rental.persistence;

import io.quarkus.mongodb.panache.PanacheMongoEntity;

import java.time.LocalDate;

/**
 * Entidade MongoDB espelho do domínio {@code Rental}. Skeleton: o {@code id}
 * passa a ser um {@code ObjectId} (campo herdado de {@code PanacheMongoEntity}),
 * decisão a ser alinhada com o domínio na migração do ch.7.
 */
public class RentalEntity extends PanacheMongoEntity {

    private String userId;
    private Long reservationId;
    private LocalDate startDate;

    public RentalEntity() {
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(String userId) {
        this.userId = userId;
    }

    public Long getReservationId() {
        return reservationId;
    }

    public void setReservationId(Long reservationId) {
        this.reservationId = reservationId;
    }

    public LocalDate getStartDate() {
        return startDate;
    }

    public void setStartDate(LocalDate startDate) {
        this.startDate = startDate;
    }
}