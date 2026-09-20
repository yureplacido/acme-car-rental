package org.acme.rental.api;

import io.quarkus.logging.Log;
import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.acme.rental.model.Rental;
import org.acme.rental.repository.RentalRepository;

import java.util.List;

/**
 * Livro 7.6.2 - API REST de aluguel persistindo em MongoDB via repositório Panache;
 * expõe o {@code model.Rental} (POJO). Sem anotações {@code @Produces}/@Consumes
 * explícitas, pois o JSON (Jackson) é o content type default do Quarkus REST.
 */
@Path("/rental")
public class RentalResource {

    @Inject
    RentalRepository rentalRepository;

    @Path("/start/{userId}/{reservationId}")
    @POST
    public Rental start(String userId, Long reservationId) {
        Log.infof("Starting rental for %s with reservation %s", userId, reservationId);
        return rentalRepository.start(userId, reservationId);
    }

    @PUT
    @Path("/end/{userId}/{reservationId}")
    public Rental end(String userId, Long reservationId) {
        Log.infof("Ending rental for %s with reservation %s", userId, reservationId);
        return rentalRepository.end(userId, reservationId)
                .orElseThrow(() -> new NotFoundException("Rental not found"));
    }

    @GET
    public List<Rental> list() {
        return rentalRepository.list();
    }

    @GET
    @Path("/active")
    public List<Rental> listActive() {
        return rentalRepository.listActive();
    }
}