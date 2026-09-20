package org.acme.rental.repository;

import org.acme.rental.model.Rental;

import java.util.List;
import java.util.Optional;

/**
 * Porta de acesso aos aluguéis — fala no {@code model.Rental} (domínio), desacoplada
 * da persistência MongoDB. Implementações concretas podem usar o Active Record ou o
 * Repository pattern sobre {@code RentalEntity}, sem tocar no modelo/API.
 */
public interface RentalRepository {

    /** Cria e persiste um aluguel ativo iniciado hoje para o par usuário/reserva. */
    Rental start(String userId, Long reservationId);

    /** Encerra o aluguel do par usuário/reserva (endDate hoje, active=false). Vazio se não existir. */
    Optional<Rental> end(String userId, Long reservationId);

    List<Rental> list();

    List<Rental> listActive();

    Optional<Rental> findByUserAndReservationIdsOptional(String userId, Long reservationId);
}