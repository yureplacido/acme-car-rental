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
    private final ConcurrentHashMap<UUID, AtomicInteger> claimAttemptsByEvent =
            new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, CompletableFuture<Void>> secondClaimAttempts =
            new ConcurrentHashMap<>();
    private final AtomicInteger claimAttempts = new AtomicInteger();

    @Override
    public Uni<Boolean> tryClaim(UUID eventId) {
        return Uni.createFrom().item(() -> {
            claimAttempts.incrementAndGet();

            int eventAttempts = claimAttemptsByEvent
                    .computeIfAbsent(eventId, ignored -> new AtomicInteger())
                    .incrementAndGet();

            if (eventAttempts == 2) {
                secondClaimAttempts
                        .computeIfAbsent(eventId, ignored -> new CompletableFuture<>())
                        .complete(null);
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
        claimAttemptsByEvent.clear();
        secondClaimAttempts.clear();
        claimAttempts.set(0);
    }

    public CompletableFuture<Void> secondClaimAttempt(UUID eventId) {
        return secondClaimAttempts.computeIfAbsent(
                eventId,
                ignored -> new CompletableFuture<>());
    }

    public int claimAttempts() {
        return claimAttempts.get();
    }

    public int claimAttempts(UUID eventId) {
        AtomicInteger attempts = claimAttemptsByEvent.get(eventId);
        return attempts == null ? 0 : attempts.get();
    }
}
