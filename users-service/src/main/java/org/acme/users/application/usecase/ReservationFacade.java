package org.acme.users.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.adapter.out.reservation.model.Reservation;
import org.acme.users.application.port.out.ReservationsGateway;

import java.time.LocalDate;
import java.util.Collection;

@ApplicationScoped
public class ReservationFacade {

    private final ReservationsGateway gateway;

    @Inject
    public ReservationFacade(ReservationsGateway gateway) {
        this.gateway = gateway;
    }

    public Collection<Reservation> listReservations() {
        return gateway.allReservations();
    }

    public Collection<Car> availableCars(LocalDate startDate, LocalDate endDate) {
        return gateway.availability(startDate, endDate);
    }

    public Reservation reserve(Long carId, LocalDate startDate, LocalDate endDate) {
        return gateway.create(Reservation.builder()
                .carId(carId)
                .startDay(startDate)
                .endDay(endDate)
                .build());
    }
}
