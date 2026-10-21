package org.acme.users.adapter.out.reservation;

import io.quarkus.oidc.token.propagation.common.AccessToken;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import org.acme.users.adapter.out.reservation.model.Car;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;
import java.util.Collection;

/**
 * Cliente so da leitura de disponibilidade, com configKey propria.
 *
 * <p>Existe por causa de um acoplamento que a separacao resolve: {@code quarkus.rest-client.<configKey>.read-timeout}
 * e <b>por cliente</b>, nao por metodo. Com os dois caminhos no {@link ReservationsClient}, o
 * prazo curto calibrado para a leitura passaria a valer tambem para o {@code POST /reservations},
 * que e <b>nao idempotente</b> (restricao medida em {@code StartRentalTest}, ADR 009) - e um
 * prazo curto numa escrita nao idempotente a transforma em escrita de resultado incerto, que e
 * exatamente o modo que a ADR proibe repetir. Separar as duas fronteiras torna a politica
 * verificavel: {@code ReservationsRestGatewayTest} fixa o prazo desta e o default daquela.
 *
 * <p>A URL e a mesma ({@code stork://reservations}); o que muda e o que a config enxerga.
 */
@Path("reservations")
@RegisterRestClient(configKey = "reservations-availability")
@AccessToken
public interface AvailabilityClient {

    @GET
    @Path("availability")
    Collection<Car> availability(@RestQuery LocalDate startDate,
                                 @RestQuery LocalDate endDate);
}
