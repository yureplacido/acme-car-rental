package org.acme.billing.adapter.out.observability;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.InvoiceOpened;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class MicrometerOutboxMetricsTest {

    private static final Duration AWAIT = Duration.ofSeconds(5);

    @Test
    void shouldCountRelayedEventsAsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerOutboxMetrics metrics =
                new MicrometerOutboxMetrics(registry, new FakeOutboxEventStore(0));

        metrics.eventRelayed();
        metrics.eventRelayed();

        assertEquals(
                2.0,
                registry.get("billing.outbox.published").counter().count(),
                0.0);
    }

    @Test
    void shouldCountRelayFailuresAsCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerOutboxMetrics metrics =
                new MicrometerOutboxMetrics(registry, new FakeOutboxEventStore(0));

        metrics.relayFailed();

        assertEquals(
                1.0,
                registry.get("billing.outbox.failures").counter().count(),
                0.0);
    }

    @Test
    void shouldExposePendingBacklogAsGaugeAfterRefresh() {
        MeterRegistry registry = new SimpleMeterRegistry();
        MicrometerOutboxMetrics metrics =
                new MicrometerOutboxMetrics(registry, new FakeOutboxEventStore(3));

        metrics.refreshBacklog().await().atMost(AWAIT);

        assertEquals(
                3.0,
                registry.get("billing.outbox.pending").gauge().value(),
                0.0);
    }

    @Test
    void shouldKeepLastBacklogValueAndCountErrorWhenRefreshFails() {
        MeterRegistry registry = new SimpleMeterRegistry();
        FakeOutboxEventStore store = new FakeOutboxEventStore(3);
        MicrometerOutboxMetrics metrics =
                new MicrometerOutboxMetrics(registry, store);

        metrics.refreshBacklog().await().atMost(AWAIT);
        store.failOnCountPending();
        assertThrows(
                IllegalStateException.class,
                () -> metrics.refreshBacklog().await().atMost(AWAIT));

        assertEquals(
                3.0,
                registry.get("billing.outbox.pending").gauge().value(),
                0.0);
        assertEquals(
                1.0,
                registry.get("billing.outbox.backlog.refresh.errors").counter().count(),
                0.0);
    }

    private static final class FakeOutboxEventStore implements OutboxEventStore {

        private final long pendingCount;
        private boolean failCountPending;

        private FakeOutboxEventStore(long pendingCount) {
            this.pendingCount = pendingCount;
        }

        private void failOnCountPending() {
            this.failCountPending = true;
        }

        @Override
        public Uni<Void> appendInvoiceOpened(InvoiceOpened event) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<List<OutboxEvent>> findPending(int limit) {
            return Uni.createFrom().item(new ArrayList<>());
        }

        @Override
        public Uni<Void> markPublished(OutboxEvent event, Instant publishedAt) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Void> incrementAttempts(OutboxEvent event) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Long> countPending() {
            if (failCountPending) {
                return Uni.createFrom().failure(
                        new IllegalStateException("backlog read failed"));
            }
            return Uni.createFrom().item(pendingCount);
        }
    }
}