package org.acme.reservation.application;

import io.smallrye.mutiny.Uni;
import org.acme.reservation.application.exception.InventoryUnavailable;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.port.out.ReservationRepository;
import org.acme.reservation.application.query.AvailableVehicle;
import org.acme.reservation.application.usecase.FindAvailableVehicles;
import org.acme.reservation.domain.model.CustomerId;
import org.acme.reservation.domain.model.RentalPeriod;
import org.acme.reservation.domain.model.Reservation;
import org.acme.reservation.domain.model.ReservationId;
import org.acme.reservation.domain.model.ReservationStatus;
import org.acme.reservation.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Especificacao do caso de uso de disponibilidade: indisponibilidade do inventory e
 * "nenhum veiculo disponivel" sao resultados diferentes. O primeiro falha; o segundo
 * devolve lista vazia. Sem essa distincao o @Fallback do adapter seria tentado a
 * degradar a falha em lista vazia (docs/adr/009).
 */
class FindAvailableVehiclesTest {

    private static final LocalDate START = LocalDate.of(2035, 6, 1);
    private static final LocalDate END = LocalDate.of(2035, 6, 10);

    private final ReservationRepository noReservations = new RepositoryWith();

    @Test
    void shouldFailWhenInventoryCannotBeReached() {
        FindAvailableVehicles useCase = new FindAvailableVehicles(
                new FailingInventoryGateway(new RuntimeException("connection refused")),
                noReservations);

        InventoryUnavailable failure = assertThrows(InventoryUnavailable.class,
                () -> useCase.handle(START, END).await().indefinitely());

        assertEquals("inventory is unavailable", failure.getMessage());
        assertEquals("connection refused", failure.getCause().getMessage());
    }

    @Test
    void shouldServeEmptyAvailabilityWhenInventoryRespondsWithoutVehicles() {
        List<AvailableVehicle> result = new FindAvailableVehicles(new EmptyInventoryGateway(), noReservations)
                .handle(START, END)
                .await()
                .indefinitely();

        assertEquals(List.of(), result);
    }

    @Test
    void shouldHideVehiclesAlreadyReservedForTheRequestedPeriod() {
        ReservationRepository withConflict = new RepositoryWith(
                Reservation.rehydrate(
                        new ReservationId(42L),
                        new CustomerId("bob"),
                        new VehicleId(10L),
                        new RentalPeriod(LocalDate.of(2035, 6, 5), LocalDate.of(2035, 6, 7)),
                        ReservationStatus.PENDING));

        List<AvailableVehicle> result = new FindAvailableVehicles(new TwoVehiclesGateway(), withConflict)
                .handle(START, END)
                .await()
                .indefinitely();

        assertEquals(List.of(new AvailableVehicle(20L, "BBB-2", "Fiat", "Panda")), result);
    }

    static class RepositoryWith implements ReservationRepository {

        private final List<Reservation> reservations;

        RepositoryWith(Reservation... reservations) {
            this.reservations = List.of(reservations);
        }

        public Uni<List<Reservation>> all() {
            return Uni.createFrom().item(reservations);
        }

        public Uni<Reservation> save(Reservation reservation) {
            throw new UnsupportedOperationException();
        }

        public Uni<List<Reservation>> findByVehicle(VehicleId vehicleId) {
            throw new UnsupportedOperationException();
        }
    }

    static class FailingInventoryGateway implements InventoryGateway {

        private final RuntimeException cause;

        FailingInventoryGateway(RuntimeException cause) {
            this.cause = cause;
        }

        public Uni<List<AvailableVehicle>> findVehicles() {
            return Uni.createFrom().failure(new InventoryUnavailable(cause));
        }
    }

    static class EmptyInventoryGateway implements InventoryGateway {

        public Uni<List<AvailableVehicle>> findVehicles() {
            return Uni.createFrom().item(List.of());
        }
    }

    static class TwoVehiclesGateway implements InventoryGateway {

        public Uni<List<AvailableVehicle>> findVehicles() {
            return Uni.createFrom().item(List.of(
                    new AvailableVehicle(10L, "AAA-1", "Renault", "Clio"),
                    new AvailableVehicle(20L, "BBB-2", "Fiat", "Panda")));
        }
    }
}
