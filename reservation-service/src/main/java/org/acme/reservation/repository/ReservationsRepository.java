package org.acme.reservation.repository;

import org.acme.reservation.model.Reservation;

import java.util.List;

public interface ReservationsRepository {

    List<Reservation> findAll();

    Reservation save(Reservation reservation);
}