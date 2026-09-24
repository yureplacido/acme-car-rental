package org.acme.billing.application;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.ReservationConfirmed;
import org.acme.billing.application.usecase.ConsumeReservationConfirmed;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ConsumeReservationConfirmedTest {

    @Test
    void shouldDelegateEventProcessing() {
        AtomicInteger attempts = new AtomicInteger();
        ConsumeReservationConfirmed consumer = new ConsumeReservationConfirmed(
                event -> {
                    attempts.incrementAndGet();
                    return Uni.createFrom().voidItem();
                });

        consumer.handle(event()).await().atMost(Duration.ofSeconds(5));

        assertEquals(1, attempts.get());
    }

    @Test
    void shouldPropagateProcessingFailure() {
        ConsumeReservationConfirmed consumer = new ConsumeReservationConfirmed(
                event -> Uni.createFrom().failure(
                        new IllegalStateException("processing failed")));

        assertThrows(IllegalStateException.class,
                () -> consumer.handle(event()).await().atMost(Duration.ofSeconds(5)));
    }

    private static ReservationConfirmed event() {
        return new ReservationConfirmed(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                "reservation-42",
                "alice",
                7L,
                "ABC-1234",
                "2026-10-01",
                "2026-10-05",
                new ReservationConfirmed.Money(new BigDecimal("150.00"), "BRL"));
    }
}