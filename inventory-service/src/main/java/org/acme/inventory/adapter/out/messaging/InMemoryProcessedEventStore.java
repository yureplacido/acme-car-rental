package org.acme.inventory.adapter.out.messaging;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.application.port.out.ProcessedEventStore;

import io.smallrye.mutiny.Uni;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@ApplicationScoped
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    @Override
    public Uni<Boolean> markIfNew(UUID eventId) {
        return Uni.createFrom().item(processed.add(eventId));
    }
}
