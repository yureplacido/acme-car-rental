package org.acme.users.adapter.in.web.templates;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.adapter.out.reservation.model.Reservation;

import java.time.LocalDate;
import java.util.Collection;

@CheckedTemplate(basePath = "ReservationsTemplates")
public class ReservationsTemplates {

    public static native TemplateInstance index(LocalDate startDate,
                                                LocalDate endDate,
                                                String name);

    public static native TemplateInstance listofreservations(Collection<Reservation> reservations);

    public static native TemplateInstance availablecars(Collection<Car> cars,
                                                        LocalDate startDate,
                                                        LocalDate endDate);
}
