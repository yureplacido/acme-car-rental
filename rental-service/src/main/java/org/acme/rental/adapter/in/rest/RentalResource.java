package org.acme.rental.adapter.in.rest;

import jakarta.inject.Inject;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.PUT;
import jakarta.ws.rs.Path;
import org.acme.rental.application.usecase.EndRental;
import org.acme.rental.application.usecase.ListRentals;
import org.acme.rental.application.usecase.StartRental;
import org.acme.rental.domain.model.Rental;

import java.time.LocalDate;
import java.util.List;

@Path("/rental")
public class RentalResource {

    private final StartRental startRental;
    private final EndRental endRental;
    private final ListRentals listRentals;

    @Inject
    public RentalResource(StartRental startRental,
                          EndRental endRental,
                          ListRentals listRentals) {
        this.startRental = startRental;
        this.endRental = endRental;
        this.listRentals = listRentals;
    }

    @POST
    @Path("/start/{userId}/{reservationId}")
    public RentalResponse start(@jakarta.ws.rs.PathParam("userId") String userId,
                                @jakarta.ws.rs.PathParam("reservationId") Long reservationId) {
        return RentalResponse.from(
                startRental.handle(new StartRental.Command(userId, reservationId, LocalDate.now())));
    }

    @PUT
    @Path("/end/{userId}/{reservationId}")
    public RentalResponse end(@jakarta.ws.rs.PathParam("userId") String userId,
                              @jakarta.ws.rs.PathParam("reservationId") Long reservationId) {
        try {
            return RentalResponse.from(
                    endRental.handle(new EndRental.Command(userId, reservationId, LocalDate.now())));
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Rental not found", e);
        }
    }

    @GET
    public List<RentalResponse> list() {
        return listRentals.handle(false).stream().map(RentalResponse::from).toList();
    }

    @GET
    @Path("/active")
    public List<RentalResponse> listActive() {
        return listRentals.handle(true).stream().map(RentalResponse::from).toList();
    }
}
