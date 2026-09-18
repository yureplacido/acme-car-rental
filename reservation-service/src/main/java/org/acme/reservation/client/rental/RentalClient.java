package org.acme.reservation.client.rental;

import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import org.eclipse.microprofile.rest.client.inject.RegisterRestClient;
import org.jboss.resteasy.reactive.RestPath;

@Path("/rental")
@RegisterRestClient
public interface RentalClient {

    @POST
    @Path("/start/{userId}/{reservationId}")
    Rental start(@RestPath String userId, @RestPath Long reservationId);

}
