package org.acme.reservation.repository;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.reservation.entity.ReservationEntity;
import org.acme.reservation.model.Reservation;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Implementação reativa (Repository pattern via {@link PanacheRepository}) sobre
 * {@link ReservationEntity}, mapeando para o modelo de domínio com {@link ReservationMapper}.
 * É a impl padrão do contrato {@link ReservationRepository}; pode ser trocada por uma
 * Active Record sem alterar a API.
 *
 * <p>{@link WithSession} garante sessão no contexto para os métodos do repositório
 * (diferente dos statics da entidade, que abrem sessão on demand).</p>
 */
@ApplicationScoped
@WithSession
public class PanacheReservationRepository implements ReservationRepository, PanacheRepository<ReservationEntity> {

    @Override
    public Uni<List<Reservation>> all() {
        return listAll().onItem().transform(list -> list.stream()
                .map(ReservationMapper::toModel)
                .collect(Collectors.toList()));
    }

    @Override
    public Uni<Reservation> save(Reservation reservation) {
        ReservationEntity entity = ReservationMapper.toEntity(reservation);
        return persist(entity).map(ReservationMapper::toModel);
    }
}