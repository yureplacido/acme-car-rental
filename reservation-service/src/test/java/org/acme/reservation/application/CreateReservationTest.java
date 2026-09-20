package org.acme.reservation.application;

import io.smallrye.mutiny.Uni;
import org.acme.reservation.application.port.out.RentalGateway;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.acme.reservation.application.usecase.CreateReservation;
import org.acme.reservation.domain.model.CustomerId;
import org.acme.reservation.domain.model.RentalPeriod;
import org.acme.reservation.domain.model.Reservation;
import org.acme.reservation.domain.model.ReservationStatus;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class CreateReservationTest {

    private final ReservationRepository repository = mock(ReservationRepository.class);
    private final RentalGateway rentalGateway = mock(RentalGateway.class);
    private final CreateReservation service = new CreateReservation(repository, rentalGateway);

    @Test
    void shouldCreatePendingReservationForCustomer() {
        Reservation requested = Reservation.create(
                new CustomerId("alice"),
                new org.acme.reservation.domain.model.VehicleId(10L),
                new RentalPeriod(LocalDate.of(2035, 3, 20), LocalDate.of(2035, 3, 29)));

        Reservation persisted = Reservation.rehydrate(
                new org.acme.reservation.domain.model.ReservationId(42L),
                requested.customerId(),
                requested.vehicleId(),
                requested.period(),
                ReservationStatus.PENDING);

        when(repository.findByVehicle(any())).thenReturn(Uni.createFrom().item(List.of()));
        when(repository.save(any())).thenReturn(Uni.createFrom().item(persisted));

        Reservation result = service.handle(new CreateReservation.Command(
                "alice", 10L,
                LocalDate.of(2035, 3, 20),
                LocalDate.of(2035, 3, 29),
                LocalDate.of(2035, 3, 1))).await().indefinitely();

        assertEquals(42L, result.id().value());
        assertEquals("alice", result.customerId().value());
        assertEquals(ReservationStatus.PENDING, result.status());
        verify(repository).save(any(Reservation.class));
        verifyNoInteractions(rentalGateway);
    }

    @Test
    void shouldStartRentalWhenReservationStartsToday() {
        Reservation persisted = Reservation.rehydrate(
                new org.acme.reservation.domain.model.ReservationId(42L),
                new org.acme.reservation.domain.model.CustomerId("alice"),
                new org.acme.reservation.domain.model.VehicleId(10L),
                new org.acme.reservation.domain.model.RentalPeriod(
                        LocalDate.of(2035, 3, 20),
                        LocalDate.of(2035, 3, 29)),
                org.acme.reservation.domain.model.ReservationStatus.PENDING);

        when(repository.findByVehicle(any())).thenReturn(Uni.createFrom().item(List.of()));
        when(repository.save(any())).thenReturn(Uni.createFrom().item(persisted));
        when(rentalGateway.start("alice", 42L)).thenReturn(Uni.createFrom().voidItem());

        service.handle(new CreateReservation.Command(
                "alice", 10L,
                LocalDate.of(2035, 3, 20),
                LocalDate.of(2035, 3, 29),
                LocalDate.of(2035, 3, 20))).await().indefinitely();

        verify(rentalGateway).start("alice", 42L);
    }

    @Test
    void shouldRejectOverlappingVehicleReservation() {
        when(repository.findByVehicle(any())).thenReturn(Uni.createFrom().item(List.of(Reservation.rehydrate(
                new org.acme.reservation.domain.model.ReservationId(99L),
                new org.acme.reservation.domain.model.CustomerId("bob"),
                new org.acme.reservation.domain.model.VehicleId(10L),
                new org.acme.reservation.domain.model.RentalPeriod(
                        LocalDate.of(2035, 3, 22),
                        LocalDate.of(2035, 3, 28)),
                org.acme.reservation.domain.model.ReservationStatus.CONFIRMED))));

        var result = service.handle(new CreateReservation.Command(
                "alice", 10L,
                LocalDate.of(2035, 3, 20),
                LocalDate.of(2035, 3, 29),
                LocalDate.of(2035, 3, 1)));

        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalStateException.class,
                () -> result.await().indefinitely());

        verify(repository, never()).save(any());
        verifyNoInteractions(rentalGateway);
    }
}
