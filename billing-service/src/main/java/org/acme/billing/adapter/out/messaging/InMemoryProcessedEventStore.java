package org.acme.billing.adapter.out.messaging;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.port.out.ProcessedEventStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Store em memória usado apenas nos testes unitários do middleware. Em produção o
 * bean CDI real é {@code PostgresProcessedEventStore} (ADR 005).
 */
public class InMemoryProcessedEventStore implements ProcessedEventStore {

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();

    @Override
    public Uni<Boolean> tryClaim(UUID eventId) {
        return Uni.createFrom().item(() -> processed.add(eventId));
    }

}
