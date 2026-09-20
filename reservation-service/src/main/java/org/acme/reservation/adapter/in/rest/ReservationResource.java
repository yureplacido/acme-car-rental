package org.acme.reservation.adapter.in.rest;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.reservation.adapter.in.rest.model.ReservationRequest;
import org.acme.reservation.adapter.in.rest.model.ReservationResponse;
import org.acme.reservation.application.usecase.CreateReservation;
import org.acme.reservation.application.usecase.ListReservations;
import org.acme.reservation.application.usecase.FindAvailableVehicles;
import org.acme.reservation.application.query.AvailableVehicle;
import org.acme.reservation.security.CurrentUser;

import java.time.LocalDate;
import java.util.List;

@Path("/reservations")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReservationResource {

    private final CreateReservation createReservation;
    private final ListReservations listReservations;
    private final FindAvailableVehicles findAvailableVehicles;
    private final CurrentUser currentUser;

    @Inject
    public ReservationResource(CreateReservation createReservation,
                               ListReservations listReservations,
                               FindAvailableVehicles findAvailableVehicles,
                               CurrentUser currentUser) {
        this.createReservation = createReservation;
        this.listReservations = listReservations;
        this.findAvailableVehicles = findAvailableVehicles;
        this.currentUser = currentUser;
    }

    @POST
    @WithTransaction
    public Uni<ReservationResponse> create(ReservationRequest request) {
        return createReservation.handle(new CreateReservation.Command(
                        currentUser.getUserId() == null ? "anonymous" : currentUser.getUserId(),
                        request.carId(),
                        request.startDay(),
                        request.endDay(),
                        LocalDate.now()))
                .map(ReservationResponse::from);
    }

    @GET
    @Path("all")
    public Uni<List<ReservationResponse>> all() {
        return listReservations.handle(currentUser.getUserId())
                .map(items -> items.stream().map(ReservationResponse::from).toList());
    }

    @GET
    @Path("availability")
    public Uni<List<AvailableVehicle>> availability(LocalDate startDate, LocalDate endDate) {
        return findAvailableVehicles.handle(startDate, endDate);
    }
}
