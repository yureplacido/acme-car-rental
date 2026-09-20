package org.acme.reservation.adapter.out.rental;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.RentalGateway;
import org.eclipse.microprofile.rest.client.inject.RestClient;

@ApplicationScoped
public class RentalRestGateway implements RentalGateway {

    private final RentalClient client;

    @Inject
    public RentalRestGateway(@RestClient RentalClient client) {
        this.client = client;
    }

    @Override
    public Uni<Void> start(String customerId, Long reservationId) {
        return client.start(customerId, reservationId).replaceWithVoid();
    }
}
