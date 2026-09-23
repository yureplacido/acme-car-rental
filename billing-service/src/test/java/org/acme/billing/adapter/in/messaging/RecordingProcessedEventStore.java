package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import io.quarkus.test.Mock;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.ProcessedEventStore;

import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Mock
@ApplicationScoped
public class RecordingProcessedEventStore implements ProcessedEventStore {

    private final Set<UUID> processed = ConcurrentHashMap.newKeySet();
    private final AtomicInteger claimAttempts = new AtomicInteger();
    private CompletableFuture<Void> secondClaimAttempt = new CompletableFuture<>();

    @Override
    public Uni<Boolean> tryClaim(UUID eventId) {
        return Uni.createFrom().item(() -> {
            if (claimAttempts.incrementAndGet() == 2) {
                secondClaimAttempt.complete(null);
            }
            return processed.add(eventId);
        });
    }

    @Override
    public Uni<Void> release(UUID eventId) {
        processed.remove(eventId);
        return Uni.createFrom().voidItem();
    }

    public void reset() {
        processed.clear();
        claimAttempts.set(0);
        secondClaimAttempt = new CompletableFuture<>();
    }

    public CompletableFuture<Void> secondClaimAttempt() {
        return secondClaimAttempt;
    }

    public int claimAttempts() {
        return claimAttempts.get();
    }
}
