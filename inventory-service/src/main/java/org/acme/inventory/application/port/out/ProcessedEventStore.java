package org.acme.inventory.application.port.out;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface ProcessedEventStore {

    /**
     * Checks whether an event was successfully processed.
     */
    Uni<Boolean> isProcessed(UUID eventId);

    /**
     * Records an event after its processing completes successfully.
     */
    Uni<Void> markProcessed(UUID eventId);
}
