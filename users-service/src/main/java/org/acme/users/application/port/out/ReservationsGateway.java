package org.acme.users.application.port.out;

import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.model.ReservationView;

import java.time.LocalDate;
import java.util.Collection;

public interface ReservationsGateway {
    Collection<ReservationView> allReservations();
    ReservationView create(org.acme.users.application.model.ReservationView reservation);
    Collection<AvailableCar> availability(LocalDate startDate, LocalDate endDate);
}
