package org.acme.users.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.model.ReservationView;
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

    public Collection<ReservationView> listReservations() {
        return gateway.allReservations();
    }

    public Collection<AvailableCar> availableCars(LocalDate startDate, LocalDate endDate) {
        return gateway.availability(startDate, endDate);
    }

    public ReservationView reserve(Long carId, LocalDate startDate, LocalDate endDate) {
        return gateway.create(new ReservationView(null, null, carId, startDate, endDate, null));
    }
}
