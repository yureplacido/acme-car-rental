package org.acme.reservation.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import org.acme.reservation.domain.model.ReservationStatus;

import java.time.LocalDate;

@Entity
@Table(name = "reservation")
public class ReservationEntity extends PanacheEntity {

    public Long carId;
    public String userId;

    public LocalDate startDay;
    public LocalDate endDay;

    @Enumerated(EnumType.STRING)
    public ReservationStatus status;
}
