package org.acme.users.application;

import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.adapter.out.reservation.model.Reservation;
import org.acme.users.application.port.out.ReservationsGateway;
import org.acme.users.application.usecase.ReservationFacade;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ReservationFacadeTest {

    @Test
    void shouldDelegateReservationCreationToGateway() {
        FakeGateway gateway = new FakeGateway();
        ReservationFacade facade = new ReservationFacade(gateway);

        facade.reserve(99L, LocalDate.of(2035, 3, 20), LocalDate.of(2035, 3, 29));

        assertNotNull(gateway.created);
        assertEquals(99L, gateway.created.getCarId());
        assertEquals(LocalDate.of(2035, 3, 20), gateway.created.getStartDay());
    }

    static class FakeGateway implements ReservationsGateway {
        Reservation created;
        public java.util.Collection<Reservation> allReservations() { return List.of(); }
        public Reservation create(Reservation reservation) { created = reservation; return reservation; }
        public java.util.Collection<Car> availability(LocalDate startDate, LocalDate endDate) { return List.of(); }
    }
}
