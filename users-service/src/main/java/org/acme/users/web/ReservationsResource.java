package org.acme.users.web;

import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.users.client.ReservationsClient;
import org.acme.users.model.Car;
import org.acme.users.model.Reservation;
import org.acme.users.security.CurrentUser;
import org.acme.users.templates.ReservationsTemplates;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.LocalDate;
import java.util.Collection;

@Path("/")
public class ReservationsResource {

    private final CurrentUser currentUser;
    private final ReservationsClient client;

    public ReservationsResource(CurrentUser currentUser, @RestClient ReservationsClient client) {
        this.currentUser = currentUser;
        this.client = client;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index(@RestQuery LocalDate startDate,
                                  @RestQuery LocalDate endDate) {
        if (startDate == null) {
            startDate = LocalDate.now().plusDays(1L);
        }
        if (endDate == null) {
            endDate = LocalDate.now().plusDays(7);
        }
        return ReservationsTemplates.index(startDate, endDate,
                currentUser.getDisplayName());
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/get")
    public TemplateInstance getReservations() {
        Collection<Reservation> reservationCollection = client.allReservations();
        return ReservationsTemplates.listofreservations(reservationCollection);
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/available")
    public TemplateInstance getAvailableCars(@RestQuery LocalDate startDate,
                                             @RestQuery LocalDate endDate) {
        Collection<Car> availableCars = client.availability(startDate, endDate);
        return ReservationsTemplates.availablecars(availableCars, startDate, endDate);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    @Path("/reserve")
    public RestResponse<TemplateInstance> create(@RestForm LocalDate startDate,
                                                 @RestForm LocalDate endDate,
                                                 @RestForm Long carId) {
        client.make(Reservation.builder()
                .startDay(startDate)
                .endDay(endDate)
                .carId(carId)
                .build());
        return RestResponse.ResponseBuilder
                .ok(getReservations())
                .header("HX-Trigger-After-Swap", "update-available-cars-list")
                .build();
    }
}