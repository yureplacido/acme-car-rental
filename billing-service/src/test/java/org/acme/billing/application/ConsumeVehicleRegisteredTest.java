package org.acme.billing.application;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.VehicleRegistered;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.acme.billing.application.usecase.ConsumeVehicleRegistered;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
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

    private static VehicleRegistered event(String licensePlate) {
        return new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                licensePlate);
    }

    static class InMemoryProcessedEventStore implements ProcessedEventStore {
        private final Set<UUID> processed = new HashSet<>();

        @Override
        public synchronized Uni<Boolean> isProcessed(UUID eventId) {
            return Uni.createFrom().item(processed.contains(eventId));
        }

        @Override
        public synchronized Uni<Void> markProcessed(UUID eventId) {
            processed.add(eventId);
            return Uni.createFrom().voidItem();
        }
    }
}
