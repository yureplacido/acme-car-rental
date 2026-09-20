package org.acme.users.adapter.in.web;

import io.quarkus.qute.TemplateInstance;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.users.adapter.in.security.CurrentUser;
import org.acme.users.adapter.in.web.templates.ReservationsTemplates;
import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.model.ReservationView;
import org.acme.users.application.usecase.ReservationFacade;
import org.jboss.resteasy.reactive.RestForm;
import org.jboss.resteasy.reactive.RestQuery;
import org.jboss.resteasy.reactive.RestResponse;

import java.time.LocalDate;

@Path("/")
public class ReservationsResource {

    private final CurrentUser currentUser;
    private final ReservationFacade facade;

    public ReservationsResource(CurrentUser currentUser, ReservationFacade facade) {
        this.currentUser = currentUser;
        this.facade = facade;
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    public TemplateInstance index(@RestQuery LocalDate startDate,
                                  @RestQuery LocalDate endDate) {
        LocalDate start = startDate == null ? LocalDate.now().plusDays(1) : startDate;
        LocalDate end = endDate == null ? LocalDate.now().plusDays(7) : endDate;
        return ReservationsTemplates.index(start, end, currentUser.getDisplayName());
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/get")
    public TemplateInstance getReservations() {
        return ReservationsTemplates.listofreservations(facade.listReservations());
    }

    @GET
    @Produces(MediaType.TEXT_HTML)
    @Path("/available")
    public TemplateInstance getAvailableCars(@RestQuery LocalDate startDate,
                                             @RestQuery LocalDate endDate) {
        return ReservationsTemplates.availablecars(
                facade.availableCars(startDate, endDate), startDate, endDate);
    }

    @POST
    @Produces(MediaType.TEXT_HTML)
    @Path("/reserve")
    public RestResponse<TemplateInstance> create(@RestForm LocalDate startDate,
                                                 @RestForm LocalDate endDate,
                                                 @RestForm Long carId) {
        facade.reserve(carId, startDate, endDate);
        return RestResponse.ResponseBuilder
                .ok(getReservations())
                .header("HX-Trigger-After-Swap", "update-available-cars-list")
                .build();
    }
}
