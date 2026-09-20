package org.acme.users.adapter.out.reservation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.adapter.out.reservation.model.Reservation;
import org.acme.users.application.port.out.ReservationsGateway;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.LocalDate;
import java.util.Collection;

@ApplicationScoped
public class ReservationsRestGateway implements ReservationsGateway {

    private final ReservationsClient client;

    @Inject
    public ReservationsRestGateway(@RestClient ReservationsClient client) {
        this.client = client;
    }

    @Override
    public Collection<Reservation> allReservations() {
        return client.allReservations();
    }

    @Override
    public Reservation create(Reservation reservation) {
        return client.create(reservation);
    }

    @Override
    public Collection<Car> availability(LocalDate startDate, LocalDate endDate) {
        return client.availability(startDate, endDate);
    }
}
