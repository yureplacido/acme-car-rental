package org.acme.reservation.application.port.out;

import io.smallrye.mutiny.Uni;

public interface RentalGateway {
    Uni<Void> start(String customerId, Long reservationId);
}
