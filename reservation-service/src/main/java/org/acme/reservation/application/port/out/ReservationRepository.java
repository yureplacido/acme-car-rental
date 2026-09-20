package org.acme.reservation.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.reservation.domain.model.Reservation;
import org.acme.reservation.domain.model.VehicleId;

import java.util.List;

public interface ReservationRepository {
    Uni<List<Reservation>> all();
    Uni<Reservation> save(Reservation reservation);
    Uni<List<Reservation>> findByVehicle(VehicleId vehicleId);
}
