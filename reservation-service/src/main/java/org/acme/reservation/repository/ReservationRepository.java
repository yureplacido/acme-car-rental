package org.acme.reservation.repository;

import io.smallrye.mutiny.Uni;
import org.acme.reservation.model.Reservation;

import java.util.List;

/**
 * Porta de acesso às reservas — fala no {@code model.Reservation} (domínio),
 * desacoplada da persistência reativa. Implementações concretas podem usar o
 * Active Record ou o Repository pattern sobre {@code ReservationEntity}, sem
 * tocar no modelo/API.
 */
public interface ReservationRepository {

    Uni<List<Reservation>> all();

    Uni<Reservation> save(Reservation reservation);
}