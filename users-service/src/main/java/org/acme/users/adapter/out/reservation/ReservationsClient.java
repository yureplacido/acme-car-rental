package org.acme.users.adapter.out.reservation;

import io.quarkus.oidc.token.propagation.common.AccessToken;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.acme.users.adapter.out.reservation.model.Reservation;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;

import java.util.Collection;

/**
 * Fronteira de escrita (e da listagem) contra o reservation-service, com a configKey
 * {@code reservations}. <b>Deliberadamente sem a leitura de disponibilidade</b>: um prazo de
 * transporte e propriedade de toda a configKey, e a escrita abaixo não é idempotente
 * (ADR 009). A leitura mora em {@link AvailabilityClient}, com prazo próprio.
 */
@Path("reservations")
@RegisterRestClient(configKey = "reservations")
@AccessToken
public interface ReservationsClient {

    @GET
    @Path("all")
    Collection<Reservation> allReservations();

    @POST
    Reservation create(Reservation reservation);
}
