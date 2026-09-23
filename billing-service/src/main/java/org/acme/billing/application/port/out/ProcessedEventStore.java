package org.acme.billing.application.port.out;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface ProcessedEventStore {

    Uni<Boolean> tryClaim(UUID eventId);

    Uni<Void> release(UUID eventId);
}
