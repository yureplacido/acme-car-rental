package org.acme.inventory.application.port.out;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface ProcessedEventStore {

    /**
     * Registers an event when it has not been seen before.
     *
     * @return true when this is the first observation of the event,
     *         false when the event was already observed.
     */
    Uni<Boolean> markIfNew(UUID eventId);
}
