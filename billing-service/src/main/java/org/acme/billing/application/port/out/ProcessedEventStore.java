package org.acme.billing.application.port.out;

import io.smallrye.mutiny.Uni;

import java.util.UUID;

public interface ProcessedEventStore {

    Uni<Boolean> isProcessed(UUID eventId);

    Uni<Void> markProcessed(UUID eventId);
}
