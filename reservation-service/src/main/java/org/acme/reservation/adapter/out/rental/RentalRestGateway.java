package org.acme.reservation.adapter.out.rental;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.RentalGateway;
import org.acme.reservation.client.rental.Rental;
import org.acme.reservation.client.rental.RentalClient;

@ApplicationScoped
public class RentalRestGateway implements RentalGateway {

    private final RentalClient client;

    @Inject
    public RentalRestGateway(RentalClient client) {
        this.client = client;
    }

    @Override
    public Uni<Void> start(String customerId, Long reservationId) {
        return client.start(customerId, reservationId).replaceWithVoid();
    }
}
