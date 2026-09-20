package org.acme.users.adapter.out.reservation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.adapter.out.reservation.model.Reservation;
import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.model.ReservationView;
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
    public Collection<ReservationView> allReservations() {
        return client.allReservations().stream().map(this::toView).toList();
    }

    @Override
    public ReservationView create(ReservationView reservation) {
        return toView(client.create(new Reservation()
                .builder()
                .id(reservation.id())
                .userId(reservation.userId())
                .carId(reservation.carId())
                .startDay(reservation.startDay())
                .endDay(reservation.endDay())
                .status(reservation.status())
                .build()));
    }

    @Override
    public Collection<AvailableCar> availability(LocalDate startDate, LocalDate endDate) {
        return client.availability(startDate, endDate).stream().map(this::toAvailableCar).toList();
    }

    private ReservationView toView(Reservation value) {
        return new ReservationView(
                value.getId(), value.getUserId(), value.getCarId(),
                value.getStartDay(), value.getEndDay(), value.getStatus());
    }

    private AvailableCar toAvailableCar(Car value) {
        return new AvailableCar(
                value.getId(), value.getLicensePlateNumber(),
                value.getManufacturer(), value.getModel());
    }
}
