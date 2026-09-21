package org.acme.inventory.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.inventory.domain.event.VehicleRegistered;

public interface EventPublisher {
    Uni<Void> publish(VehicleRegistered event);
}
