package org.acme.reservation.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.reservation.application.query.AvailableVehicle;

import java.time.LocalDate;
import java.util.List;

public interface InventoryGateway {
    Uni<List<AvailableVehicle>> findVehicles();
}
