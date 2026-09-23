package org.acme.billing.adapter.out.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.ProcessedEventStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    @Override
    public Uni<Boolean> tryClaim(UUID eventId) {
        return Uni.createFrom().item(() -> processed.add(eventId));
    }

    @Override
    public Uni<Void> release(UUID eventId) {
        processed.remove(eventId);
        return Uni.createFrom().voidItem();
    }
}
