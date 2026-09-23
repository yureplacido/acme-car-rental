package org.acme.billing.application;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.VehicleRegistered;
import org.acme.billing.application.usecase.ConsumeVehicleRegistered;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumeVehicleRegisteredTest {

    @Test
    void shouldDelegateEventProcessing() {
        AtomicInteger attempts = new AtomicInteger();

        ConsumeVehicleRegistered consumer = new ConsumeVehicleRegistered(
                event -> {
                    attempts.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });

        consumer.handle(event("ABC123")).await().atMost(Duration.ofSeconds(5));

        assertEquals(1, attempts.get());
    }

    @Test
    void shouldPropagateProcessingFailure() {
        ConsumeVehicleRegistered consumer = new ConsumeVehicleRegistered(
                event -> Uni.createFrom().failure(
                        new IllegalStateException("processing failed")));

        assertThrows(
                IllegalStateException.class,
                () -> consumer.handle(event("ABC123")).await().atMost(Duration.ofSeconds(5)));
    }

    private static VehicleRegistered event(String licensePlate) {
        return new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                licensePlate);
    }
}
