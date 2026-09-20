package org.acme.users.application.port.out;

import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.adapter.out.reservation.model.Reservation;

import java.time.LocalDate;
import java.util.Collection;

public interface ReservationsGateway {
    Collection<Reservation> allReservations();
    Reservation create(Reservation reservation);
    Collection<Car> availability(LocalDate startDate, LocalDate endDate);
}
