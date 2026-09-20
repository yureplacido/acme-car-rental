package org.acme.rental.adapter.out.persistence;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rental.application.port.out.RentalRepository;
import org.acme.rental.domain.model.Rental;
import org.acme.rental.domain.model.RentalStatus;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheRentalRepository implements RentalRepository, PanacheMongoRepository<RentalEntity> {

    @Override
    public Rental save(Rental rental) {
        RentalEntity entity = RentalMapper.toEntity(rental);
        if (entity.id == null) {
            persist(entity);
        } else {
            update(entity);
        }
        return RentalMapper.toDomain(entity);
    }

    @Override
    public Optional<Rental> findByCustomerAndReservation(String customerId, Long reservationId) {
        return find("userId = ?1 and reservationId = ?2", customerId, reservationId)
                .firstResultOptional()
                .map(RentalMapper::toDomain);
    }

    @Override
    public List<Rental> findAll() {
        return listAll().stream().map(RentalMapper::toDomain).toList();
    }

    @Override
    public List<Rental> findActive() {
        return list("status", RentalStatus.ACTIVE.name()).stream()
                .map(RentalMapper::toDomain).toList();
    }
}
