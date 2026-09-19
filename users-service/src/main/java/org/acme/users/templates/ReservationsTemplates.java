package org.acme.users.templates;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import org.acme.users.model.Car;
import org.acme.users.model.Reservation;

import java.time.LocalDate;
import java.util.Collection;

@CheckedTemplate(basePath = "ReservationsTemplates")
public class ReservationsTemplates {

    public static native TemplateInstance index(LocalDate startDate,
                                                LocalDate endDate,
                                                String name);

    public static native TemplateInstance listofreservations(
            Collection<Reservation> reservations);

    public static native TemplateInstance availablecars(
            Collection<Car> cars,
            LocalDate startDate,
            LocalDate endDate);
}