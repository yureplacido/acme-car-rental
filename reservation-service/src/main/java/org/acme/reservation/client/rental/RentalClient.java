package org.acme.reservation.client.rental;

import io.smallrye.mutiny.Uni;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

@Path("/rental")
@RegisterRestClient
public interface RentalClient {

    /**
     * Livro 7.6.3/7.7: retorna {@link Uni} (chamada não-bloqueante) e o id do aluguel
     * é String ({@code ObjectId} do MongoDB seriado como string pelo rental-service).
     */
    @POST
    @Path("/start/{userId}/{reservationId}")
    Uni<Rental> start(@RestPath String userId, @RestPath Long reservationId);

}