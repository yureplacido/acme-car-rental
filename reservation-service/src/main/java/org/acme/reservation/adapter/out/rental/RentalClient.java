package org.acme.reservation.adapter.out.rental;

import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;

@Path("/rental")
@RegisterRestClient
public interface RentalClient {
    @POST
    @Path("/start/{userId}/{reservationId}")
    Uni<RentalResponse> start(@RestPath String userId, @RestPath Long reservationId);
}
