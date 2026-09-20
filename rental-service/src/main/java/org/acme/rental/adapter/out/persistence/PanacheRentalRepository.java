package org.acme.rental.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rental.application.port.out.RentalRepository;
import org.acme.rental.domain.model.Rental;
import org.acme.rental.domain.model.RentalStatus;

import java.util.List;
import java.util.Optional;

@ApplicationScoped
public class PanacheRentalRepository implements RentalRepository {

    @Override
    public Rental save(Rental rental) {
        RentalEntity entity = RentalMapper.toEntity(rental);
        if (entity.id == null) {
            entity.persist();
        } else {
            entity.update();
        }
        return RentalMapper.toDomain(entity);
    }

    @Override
    public Optional<Rental> findByCustomerAndReservation(String customerId, Long reservationId) {
        return RentalEntity.find("userId = ?1 and reservationId = ?2", customerId, reservationId)
                .firstResultOptional()
                .map(entity -> RentalMapper.toDomain((RentalEntity) entity));
    }

    @Override
    public List<Rental> findAll() {
        return RentalEntity.listAll().stream()
                .map(entity -> RentalMapper.toDomain((RentalEntity) entity))
                .toList();
    }

    @Override
    public List<Rental> findActive() {
        return RentalEntity.list("status", RentalStatus.ACTIVE.name()).stream()
                .map(entity -> RentalMapper.toDomain((RentalEntity) entity))
                .toList();
    }
}
