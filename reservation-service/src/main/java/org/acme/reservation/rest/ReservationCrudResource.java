package org.acme.reservation.rest;

import io.quarkus.hibernate.reactive.rest.data.panache.PanacheEntityResource;
import io.quarkus.rest.data.panache.ResourceProperties;
import org.acme.reservation.entity.ReservationEntity;

/**
 * Livro 7.4 ajustado à versão reativa (seção 7.7): o REST Data gera um CRUD JAX-RS
 * completo sobre a entidade Panache em {@code /reservations/admin/reservation} —
 * GET (lista/um), POST, PUT e DELETE sem escrever código de resource. O path
 * carrega o prefixo {@code /reservations} por convenção do projeto, pois o traefik
 * encaminha para o serviço sem strip (gateway agregador).
 */
@ResourceProperties(path = "/reservations/admin/reservation")
public interface ReservationCrudResource extends PanacheEntityResource<ReservationEntity, Long> {
}