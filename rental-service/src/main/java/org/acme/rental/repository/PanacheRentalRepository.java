package org.acme.rental.repository;

import io.quarkus.mongodb.panache.PanacheMongoRepository;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.rental.entity.RentalEntity;
import org.acme.rental.model.Rental;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Implementação MongoDB (Repository pattern via {@link PanacheMongoRepository}) sobre
 * {@link RentalEntity}, mapeando para o modelo de domínio com {@link RentalMapper}.
 * É a impl padrão do contrato {@link RentalRepository}; pode ser trocada por uma
 * Active Record sem alterar a API.
 */
@ApplicationScoped
public class PanacheRentalRepository implements RentalRepository, PanacheMongoRepository<RentalEntity> {

    @Override
    public Rental start(String userId, Long reservationId) {
        RentalEntity entity = new RentalEntity();
        entity.userId = userId;
        entity.reservationId = reservationId;
        entity.startDate = LocalDate.now();
        entity.active = true;
        persist(entity);
        return RentalMapper.toModel(entity);
    }

    @Override
    public Optional<Rental> end(String userId, Long reservationId) {
        Optional<RentalEntity> optionalEntity = findEntity(userId, reservationId);
        if (optionalEntity.isEmpty()) {
            return Optional.empty();
        }
        RentalEntity entity = optionalEntity.get();
        entity.endDate = LocalDate.now();
        entity.active = false;
        update(entity);
        return Optional.of(RentalMapper.toModel(entity));
    }

    @Override
    public List<Rental> list() {
        return listAll().stream()
                .map(RentalMapper::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public List<Rental> listActive() {
        return list("active", true).stream()
                .map(RentalMapper::toModel)
                .collect(Collectors.toList());
    }

    @Override
    public Optional<Rental> findByUserAndReservationIdsOptional(String userId, Long reservationId) {
        return findEntity(userId, reservationId).map(RentalMapper::toModel);
    }

    private Optional<RentalEntity> findEntity(String userId, Long reservationId) {
        return find("userId = ?1 and reservationId = ?2", userId, reservationId)
                .firstResultOptional();
    }
}