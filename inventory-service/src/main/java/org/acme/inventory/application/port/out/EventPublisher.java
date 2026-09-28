package org.acme.inventory.application.port.out;

import io.smallrye.mutiny.Uni;

public interface EventPublisher<T> {
    Uni<Void> publish(T event);
}
