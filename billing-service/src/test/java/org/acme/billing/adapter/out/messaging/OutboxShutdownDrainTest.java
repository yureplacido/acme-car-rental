package org.acme.billing.adapter.out.messaging;

import io.smallrye.mutiny.Uni;
import io.vertx.core.Vertx;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.EventPublisher;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.acme.billing.application.port.out.OutboxMetrics;
import org.acme.billing.application.usecase.PublishPendingOutboxEvents;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTimeoutPreemptively;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Graceful shutdown contract for the outbox relay: when the application shuts down, a final bounded
 * drain must publish the events still pending, and whatever happens it must never hang nor fail the
 * shutdown (the Quarkus graceful shutdown only drains HTTP requests, not scheduled work).
 */
class OutboxShutdownDrainTest {

    private static Vertx vertx;

    @BeforeAll
    static void startVertx() {
        vertx = Vertx.vertx();
    }

    @AfterAll
    static void stopVertx() {
        vertx.close();
    }

    @Test
    void shouldPublishPendingEventsOnShutdown() {
        OutboxEvent first = event();
        OutboxEvent second = event();
        RecordingStore store = new RecordingStore(List.of(first, second));
        RecordingPublisher publisher = new RecordingPublisher();

        OutboxShutdownDrain drain = new OutboxShutdownDrain(
                new PublishPendingOutboxEvents(store, publisher, store.metrics),
                Duration.ofSeconds(5),
                vertx);

        drain.drain();

        assertEquals(List.of(first.eventId(), second.eventId()), publisher.publishedIds,
                "a final drain deve publicar os eventos ainda pendentes");
        assertEquals(List.of(first.eventId(), second.eventId()), store.markedIds,
                "a final drain deve marcar os publicados");
    }

    @Test
    void shouldDoNothingWhenThereAreNoPendingEvents() {
        RecordingStore store = new RecordingStore(List.of());
        RecordingPublisher publisher = new RecordingPublisher();

        OutboxShutdownDrain drain = new OutboxShutdownDrain(
                new PublishPendingOutboxEvents(store, publisher, store.metrics),
                Duration.ofSeconds(5),
                vertx);

        assertDoesNotThrow(drain::drain);
        assertTrue(publisher.publishedIds.isEmpty());
        assertTrue(store.markedIds.isEmpty());
        assertTrue(store.incrementedIds.isEmpty());
    }

    @Test
    void shouldNotFailShutdownWhenPublicationFails() {
        OutboxEvent first = event();
        RecordingStore store = new RecordingStore(List.of(first));
        RecordingPublisher publisher = new RecordingPublisher(first.eventId());

        OutboxShutdownDrain drain = new OutboxShutdownDrain(
                new PublishPendingOutboxEvents(store, publisher, store.metrics),
                Duration.ofSeconds(5),
                vertx);

        assertDoesNotThrow(drain::drain,
                "uma falha de publicacao nao pode derrubar o shutdown");
        assertEquals(List.of(first.eventId()), store.incrementedIds,
                "a falha deve incrementar as tentativas (retry no proximo boot)");
    }

    @Test
    void shouldNotHangWhenDrainExceedsTimeout() {
        OutboxShutdownDrain drain = new OutboxShutdownDrain(
                new PublishPendingOutboxEvents(new NeverCompletingStore(), new RecordingPublisher(), new NoopMetrics()),
                Duration.ofMillis(50),
                vertx);

        assertTimeoutPreemptively(Duration.ofSeconds(2), () -> assertDoesNotThrow(drain::drain),
                "a final drain deve respeitar o timeout e nao travar o shutdown");
    }

    @Test
    void shouldCancelTheInFlightDrainWhenItTimesOut() {
        CancellableStore store = new CancellableStore();

        OutboxShutdownDrain drain = new OutboxShutdownDrain(
                new PublishPendingOutboxEvents(store, new RecordingPublisher(), new NoopMetrics()),
                Duration.ofMillis(100),
                vertx);

        drain.drain();

        assertTrue(store.cancelled.get(),
                "ao estourar o timeout a subscription em voo deve ser cancelada para nao vazar");
    }

    private static OutboxEvent event() {
        return new OutboxEvent(UUID.randomUUID(), "invoice-opened", "Invoice", UUID.randomUUID().toString(),
                "{}", Instant.now(), 0);
    }

    private static final class RecordingStore implements OutboxEventStore {

        private final List<OutboxEvent> pending;
        private final List<UUID> markedIds = new ArrayList<>();
        private final List<UUID> incrementedIds = new ArrayList<>();
        private final OutboxMetrics metrics = new NoopMetrics();

        private RecordingStore(List<OutboxEvent> pending) {
            this.pending = pending;
        }

        @Override
        public Uni<Void> appendInvoiceOpened(org.acme.billing.application.event.InvoiceOpened event) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<List<OutboxEvent>> findPending(int batchSize) {
            return Uni.createFrom().item(List.copyOf(pending));
        }

        @Override
        public Uni<Void> markPublished(OutboxEvent event, Instant publishedAt) {
            markedIds.add(event.eventId());
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Void> incrementAttempts(OutboxEvent event) {
            incrementedIds.add(event.eventId());
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<Long> countPending() {
            return Uni.createFrom().item((long) pending.size());
        }
    }

    private static final class NeverCompletingStore implements OutboxEventStore {

        @Override
        public Uni<Void> appendInvoiceOpened(org.acme.billing.application.event.InvoiceOpened event) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<List<OutboxEvent>> findPending(int batchSize) {
            return Uni.createFrom().nothing();
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
            return Uni.createFrom().item(0L);
        }
    }

    private static final class CancellableStore implements OutboxEventStore {

        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        @Override
        public Uni<Void> appendInvoiceOpened(org.acme.billing.application.event.InvoiceOpened event) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<List<OutboxEvent>> findPending(int batchSize) {
            return Uni.createFrom().<List<OutboxEvent>>nothing()
                    .onCancellation()
                    .invoke(() -> cancelled.set(true));
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
            return Uni.createFrom().item(0L);
        }
    }

    private static final class RecordingPublisher implements EventPublisher {

        private final UUID failingEventId;
        private final List<UUID> publishedIds = new ArrayList<>();

        private RecordingPublisher() {
            this(null);
        }

        private RecordingPublisher(UUID failingEventId) {
            this.failingEventId = failingEventId;
        }

        @Override
        public Uni<Void> publish(OutboxEvent event) {
            publishedIds.add(event.eventId());
            if (event.eventId().equals(failingEventId)) {
                return Uni.createFrom().failure(new IllegalStateException("publication failed"));
            }
            return Uni.createFrom().voidItem();
        }
    }

    private static final class NoopMetrics implements OutboxMetrics {

        @Override
        public void eventRelayed() {
        }

        @Override
        public void relayFailed() {
        }
    }
}
