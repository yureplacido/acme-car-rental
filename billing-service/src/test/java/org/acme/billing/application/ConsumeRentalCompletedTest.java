package org.acme.billing.application;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.RentalCompleted;
import org.acme.billing.application.usecase.ConsumeRentalCompleted;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumeRentalCompletedTest {

    @Test
    void shouldDelegateEventProcessing() {
        AtomicInteger attempts = new AtomicInteger();
        ConsumeRentalCompleted consumer = new ConsumeRentalCompleted(
                event -> {
                    attempts.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });

        consumer.handle(event()).await().atMost(Duration.ofSeconds(5));

        assertEquals(1, attempts.get());
    }

    @Test
    void shouldPropagateProcessingFailure() {
        ConsumeRentalCompleted consumer = new ConsumeRentalCompleted(
                event -> Uni.createFrom().failure(
                        new IllegalStateException("processing failed")));

        assertThrows(IllegalStateException.class,
                () -> consumer.handle(event()).await().atMost(Duration.ofSeconds(5)));
    }

    private static RentalCompleted event() {
        return new RentalCompleted(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                "rental-1",
                "reservation-42",
                "alice",
                7L,
                "ABC-1234",
                LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 5),
                new RentalCompleted.Money(new BigDecimal("150.00"), "BRL"));
    }
}