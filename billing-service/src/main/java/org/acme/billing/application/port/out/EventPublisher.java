package org.acme.billing.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.model.OutboxEvent;

public interface EventPublisher {

    Uni<Void> publish(OutboxEvent event);
}
