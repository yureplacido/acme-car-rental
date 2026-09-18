package org.acme.rental.api;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.rental.model.Rental;
import org.acme.rental.repository.RentalRepository;

import java.time.LocalDate;

@Path("/rental")
public class RentalResource {

    @Inject
    RentalRepository rentalRepository;

    @Path("/start/{userId}/{reservationId}")
    @POST
    @Produces(MediaType.APPLICATION_JSON)
    public Rental start(String userId, Long reservationId) {
        Log.infof("Starting rental for %s with reservation %s", userId, reservationId);
        return rentalRepository.save(new Rental(null, userId, reservationId, LocalDate.now()));
    }
}