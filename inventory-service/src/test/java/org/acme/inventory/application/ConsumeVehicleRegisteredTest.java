package org.acme.inventory.application;

import io.smallrye.mutiny.Uni;
import org.acme.inventory.application.port.out.ProcessedEventStore;
import org.acme.inventory.application.usecase.ConsumeVehicleRegistered;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
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
        AtomicInteger processed = new AtomicInteger();
        ConsumeVehicleRegistered consumer = new ConsumeVehicleRegistered(
                store,
                event -> {
                    processed.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.now(),
                new VehicleId(42L),
                "ABC123");

        consumer.handle(event).await().indefinitely();
        consumer.handle(event).await().indefinitely();

        assertEquals(1, processed.get());
    }

    @Test
    void shouldProcessDifferentEvents() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        AtomicInteger processed = new AtomicInteger();
        ConsumeVehicleRegistered consumer = new ConsumeVehicleRegistered(
                store,
                event -> {
                    processed.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });

        consumer.handle(event("ABC123")).await().indefinitely();
        consumer.handle(event("XYZ987")).await().indefinitely();

        assertEquals(2, processed.get());
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

    private static VehicleRegistered event(String plate) {
        return new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.now(),
                new VehicleId(42L),
                plate);
    }

    static class InMemoryProcessedEventStore implements ProcessedEventStore {
        private final Set<UUID> processed = new HashSet<>();

        @Override
        public synchronized Uni<Boolean> markIfNew(UUID eventId) {
            return Uni.createFrom().item(processed.add(eventId));
        }
    }
}
