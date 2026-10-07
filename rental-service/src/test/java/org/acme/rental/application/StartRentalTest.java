package org.acme.rental.application;

import org.acme.rental.application.port.out.RentalRepository;
import org.acme.rental.application.usecase.StartRental;
import org.acme.rental.domain.model.Rental;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class StartRentalTest {

    @Test
    void shouldStartRentalThroughRepositoryPort() {
        FakeRepository repository = new FakeRepository();
        StartRental useCase = new StartRental(repository);

        Rental rental = useCase.handle(new StartRental.Command(
                "alice", 42L, LocalDate.of(2035, 3, 20)));

        assertEquals("alice", rental.customerId().value());
        assertEquals(42L, rental.reservationId().value());
        assertTrue(repository.saved.contains(rental));
    }

    /**
     * Caracterizacao, nao aprovacao: e assim que StartRental se comporta hoje, e e por isso que
     * o reservation-service nao pode repetir a chamada de escrita. StartRental nao consulta
     * findByCustomerAndReservation antes de salvar, entao a segunda chamada para o mesmo par
     * cliente/reserva cria uma segunda locacao. Enquanto isso for verdade, a escrita fica sem
     * @Retry e sem chave de idempotencia: repetir depois de um resultado incerto duplicaria a
     * locacao. Se um dia isso mudar, este teste falha e a politica de escrita pode ser revista
     * (ADR docs/adr/009-fault-tolerance-chamadas-externas.md).
     */
    @Test
    void shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice() {
        FakeRepository repository = new FakeRepository();
        StartRental useCase = new StartRental(repository);
        StartRental.Command command = new StartRental.Command(
                "alice", 42L, LocalDate.of(2035, 3, 20));

        useCase.handle(command);
        useCase.handle(command);

        assertEquals(2, repository.saved.size(),
                "hoje a escrita nao e idempotente: e isso que proibe @Retry no chamador");
    }

    static class FakeRepository implements RentalRepository {
        final List<Rental> saved = new ArrayList<>();
        public Rental save(Rental rental) { saved.add(rental); return rental; }
        public Optional<Rental> findByCustomerAndReservation(String customerId, Long reservationId) { return Optional.empty(); }
        public List<Rental> findAll() { return List.copyOf(saved); }
        public List<Rental> findActive() { return List.copyOf(saved); }
    }
}
