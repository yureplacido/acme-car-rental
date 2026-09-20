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

    static class FakeRepository implements RentalRepository {
        final List<Rental> saved = new ArrayList<>();
        public Rental save(Rental rental) { saved.add(rental); return rental; }
        public Optional<Rental> findByCustomerAndReservation(String customerId, Long reservationId) { return Optional.empty(); }
        public List<Rental> findAll() { return List.copyOf(saved); }
        public List<Rental> findActive() { return List.copyOf(saved); }
    }
}
