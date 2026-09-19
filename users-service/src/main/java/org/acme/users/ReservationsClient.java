package org.acme.users;

import io.quarkus.oidc.token.propagation.common.AccessToken;
import org.acme.users.model.Car;
import org.acme.users.model.Reservation;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import java.time.LocalDate;
import java.util.Collection;

/**
 * Cap.6.2.2 (livro 6.8): REST client para o reservation-service.
 * A anotação @AccessToken faz o ID token do usuário autenticado ser
 * propagado no header Authorization Ex: Bearer ... em todas as chamadas.
 *
 * Adaptação: usamos o path semântico /reservations (plural, padrão adotado
 * neste projeto) em vez de /reservation do livro. A URL é resolvida via
 * quarkus.rest-client.reservations.url no application.properties.
 */
@RegisterRestClient(configKey = "reservations")
@AccessToken
@Path("reservations")
public interface ReservationsClient {

    @GET
    @Path("all")
    Collection<Reservation> allReservations();

    @POST
    Reservation make(Reservation reservation);

    @GET
    @Path("availability")
    Collection<Car> availability(@RestQuery LocalDate startDate,
                                 @RestQuery LocalDate endDate);
}