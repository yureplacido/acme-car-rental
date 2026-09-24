package org.acme.billing.application.port.out;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface ProcessedEventStore {

    /**
     * Atomically claims an event in the caller's transaction.
     *
     * <p>The implementation must not create an independent transaction.
     * The claim must participate in the same transaction as the business effect.</p>
     */
    Uni<Boolean> tryClaim(UUID eventId);
}
