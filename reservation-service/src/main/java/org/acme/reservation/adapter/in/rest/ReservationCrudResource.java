package org.acme.reservation.adapter.in.rest;

import io.quarkus.hibernate.reactive.rest.data.panache.PanacheEntityResource;
import io.quarkus.rest.data.panache.ResourceProperties;
import org.acme.reservation.adapter.out.persistence.ReservationEntity;

@ResourceProperties(path = "/reservations/admin/reservation")
public interface ReservationCrudResource extends PanacheEntityResource<ReservationEntity, Long> {
}
