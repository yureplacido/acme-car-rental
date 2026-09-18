package org.acme.rental.repository.memory;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rental.model.Rental;
import org.acme.rental.repository.RentalRepository;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicLong;

@ApplicationScoped
@IfBuildProperty(name = "app.repository", stringValue = "memory", enableIfMissing = true)
public class InMemoryRentalRepository implements RentalRepository {

    private final List<Rental> rentals = new CopyOnWriteArrayList<>();
    private final AtomicLong ids = new AtomicLong(0);

    @Override
    public List<Rental> findAll() {
        return rentals;
    }

    @Override
    public Rental save(Rental rental) {
        Rental persisted = rental.getId() == null
                ? new Rental(ids.incrementAndGet(), rental.getUserId(), rental.getReservationId(), rental.getStartDate())
                : rental;
        rentals.add(persisted);
        return persisted;
    }
}