package org.acme.users.adapter.in.web.templates;

import io.quarkus.qute.CheckedTemplate;
import io.quarkus.qute.TemplateInstance;
import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.model.ReservationView;

import java.time.LocalDate;
import java.util.Collection;

@CheckedTemplate(basePath = "ReservationsTemplates")
public class ReservationsTemplates {

    public static native TemplateInstance index(LocalDate startDate,
                                                LocalDate endDate,
                                                String name);

    public static native TemplateInstance listofreservations(Collection<ReservationView> reservations);

    public static native TemplateInstance availablecars(Collection<AvailableCar> cars,
                                                        LocalDate startDate,
                                                        LocalDate endDate);
}
