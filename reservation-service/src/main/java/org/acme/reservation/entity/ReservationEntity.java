package org.acme.reservation.entity;

import io.quarkus.hibernate.reactive.panache.PanacheEntity;
import jakarta.persistence.Entity;

import java.time.LocalDate;

/**
 * Livro 7.1/7.7 - Reserva como entidade Panache (active record) sobre Hibernate
 * Reactive. O {@code id} é autogerado (campo herdado de {@link PanacheEntity});
 * sem Lombok: Panache usa campos públicos. É apenas persistência — o domínio/API
 * usam {@code model/Reservation} via {@code ReservationMapper}.
 */
@Entity
public class ReservationEntity extends PanacheEntity {

    public Long carId;
    public LocalDate startDay;
    public LocalDate endDay;
    public String userId;
}