package org.acme.billing.application;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.VehicleRegistered;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.acme.billing.application.usecase.ConsumeVehicleRegistered;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumeVehicleRegisteredTest {

    @Test
    void shouldProcessTheSameEventOnlyOnce() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        AtomicInteger attempts = new AtomicInteger();

        ConsumeVehicleRegistered consumer = new ConsumeVehicleRegistered(
                store,
                event -> {
                    attempts.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });

        VehicleRegistered event = event("ABC123");

        consumer.handle(event).await().indefinitely();
        consumer.handle(event).await().indefinitely();

        assertEquals(1, attempts.get());
    }

    @Test
    void shouldAllowTheSameEventToBeProcessedAgainAfterFailure() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        AtomicInteger attempts = new AtomicInteger();

        ConsumeVehicleRegistered consumer = new ConsumeVehicleRegistered(
                store,
                event -> {
                    if (attempts.incrementAndGet() == 1) {
                        return Uni.createFrom().failure(
                                new IllegalStateException("processing failed"));
                    }
                    return Uni.createFrom().voidItem();
                });

        VehicleRegistered event = event("ABC123");

        assertThrows(
                IllegalStateException.class,
                () -> consumer.handle(event).await().indefinitely());

        consumer.handle(event).await().indefinitely();

        assertEquals(2, attempts.get());
    }

    @Test
    void shouldProcessTheSameEventOnlyOnceWhenHandledConcurrently() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        AtomicInteger attempts = new AtomicInteger();
        CountDownLatch handlerStarted = new CountDownLatch(1);
        CountDownLatch releaseHandler = new CountDownLatch(1);

        ConsumeVehicleRegistered consumer = new ConsumeVehicleRegistered(
                store,
                event -> {
                    attempts.incrementAndGet();
                    handlerStarted.countDown();
                    try {
                        if (!releaseHandler.await(1, TimeUnit.SECONDS)) {
                            return Uni.createFrom().failure(
                                    new IllegalStateException("Timed out waiting for concurrent delivery"));
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                        return Uni.createFrom().failure(
                                new IllegalStateException("Interrupted while processing event", e));
                    }
                    return Uni.createFrom().voidItem();
                });

        VehicleRegistered event = event("ABC123");

        CompletableFuture<Void> first =
                CompletableFuture.runAsync(() -> consumer.handle(event).await().indefinitely());

        assertEquals(true, handlerStarted.await(1, TimeUnit.SECONDS));

        CompletableFuture<Void> second =
                CompletableFuture.runAsync(() -> consumer.handle(event).await().indefinitely());

        second.join();
        releaseHandler.countDown();
        first.join();

        assertEquals(1, attempts.get());
    }

    private static VehicleRegistered event(String licensePlate) {
        return new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                licensePlate);
    }

    static class InMemoryProcessedEventStore implements ProcessedEventStore {
        private final Set<UUID> processed = ConcurrentHashMap.newKeySet();
        private final CountDownLatch concurrentChecks = new CountDownLatch(2);

        @Override
        public Uni<Boolean> tryClaim(UUID eventId) {
            concurrentChecks.countDown();
            return Uni.createFrom().item(processed.add(eventId));
        }

        @Override
        public Uni<Void> release(UUID eventId) {
            processed.remove(eventId);
            return Uni.createFrom().voidItem();
        }
    }
}
