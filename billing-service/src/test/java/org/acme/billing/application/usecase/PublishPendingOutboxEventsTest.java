package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.EventPublisher;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.acme.billing.application.port.out.OutboxMetrics;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishPendingOutboxEventsTest {

    @Test
    void shouldPublishPendingEventsAndMarkEachOneAsPublished() {
        OutboxEvent first = event("first");
        OutboxEvent second = event("second");
        FakeOutboxEventStore store = new FakeOutboxEventStore(List.of(first, second));
        FakeEventPublisher publisher = new FakeEventPublisher();
        FakeOutboxMetrics metrics = new FakeOutboxMetrics();

        PublishPendingOutboxEvents useCase =
                new PublishPendingOutboxEvents(store, publisher, metrics);

        useCase.handle().await().indefinitely();

        assertEquals(List.of(first.eventId(), second.eventId()), publisher.publishedIds);
        assertEquals(List.of(first.eventId(), second.eventId()), store.markedIds);
        assertTrue(store.incrementedIds.isEmpty());
        assertEquals(2, metrics.relayed);
        assertEquals(0, metrics.failed);
    }

    @Test
    void shouldDoNothingWhenThereAreNoPendingEvents() {
        FakeOutboxEventStore store = new FakeOutboxEventStore(List.of());
        FakeEventPublisher publisher = new FakeEventPublisher();
        FakeOutboxMetrics metrics = new FakeOutboxMetrics();

        new PublishPendingOutboxEvents(store, publisher, metrics)
                .handle()
                .await()
                .indefinitely();

        assertTrue(publisher.publishedIds.isEmpty());
        assertTrue(store.markedIds.isEmpty());
        assertTrue(store.incrementedIds.isEmpty());
        assertEquals(0, metrics.relayed);
        assertEquals(0, metrics.failed);
    }

    @Test
    void shouldIncrementAttemptsAndStopBatchWhenPublicationFails() {
        OutboxEvent first = event("first");
        OutboxEvent second = event("second");
        OutboxEvent third = event("third");

        FakeOutboxEventStore store =
                new FakeOutboxEventStore(List.of(first, second, third));
        FakeEventPublisher publisher =
                new FakeEventPublisher(second.eventId());
        FakeOutboxMetrics metrics = new FakeOutboxMetrics();

        assertThrows(
                IllegalStateException.class,
                () -> new PublishPendingOutboxEvents(store, publisher, metrics)
                        .handle()
                        .await()
                        .indefinitely());

        assertEquals(
                List.of(first.eventId(), second.eventId()),
                publisher.publishedIds);
        assertEquals(
                List.of(first.eventId()),
                store.markedIds);
        assertEquals(
                List.of(second.eventId()),
                store.incrementedIds);
        assertFalse(publisher.publishedIds.contains(third.eventId()));
        assertEquals(1, metrics.relayed);
        assertEquals(1, metrics.failed);
    }

    @Test
    void shouldUseRequestedBatchSize() {
        OutboxEvent first = event("first");
        OutboxEvent second = event("second");
        FakeOutboxEventStore store = new FakeOutboxEventStore(List.of(first, second));
        FakeEventPublisher publisher = new FakeEventPublisher();
        FakeOutboxMetrics metrics = new FakeOutboxMetrics();

        new PublishPendingOutboxEvents(store, publisher, metrics)
                .handle(1)
                .await()
                .indefinitely();

        assertEquals(List.of(first.eventId()), publisher.publishedIds);
        assertEquals(1, store.lastRequestedLimit);
        assertEquals(1, metrics.relayed);
    }

    private static OutboxEvent event(String aggregateId) {
        return new OutboxEvent(
                UUID.randomUUID(),
                "InvoiceOpened",
                "Invoice",
                aggregateId,
                "{}",
                Instant.now(),
                0);
    }

    private static final class FakeOutboxMetrics implements OutboxMetrics {

        private int relayed;
        private int failed;

        @Override
        public void eventRelayed() {
            relayed++;
        }

        @Override
        public void relayFailed() {
            failed++;
        }
    }

    private static final class FakeOutboxEventStore implements OutboxEventStore {

        private final List<OutboxEvent> pending;
        private final List<UUID> markedIds = new ArrayList<>();
        private final List<UUID> incrementedIds = new ArrayList<>();
        private int lastRequestedLimit;

        private FakeOutboxEventStore(List<OutboxEvent> pending) {
            this.pending = pending;
        }

        @Override
        public Uni<Void> appendInvoiceOpened(
                org.acme.billing.application.event.InvoiceOpened event) {
            return Uni.createFrom().voidItem();
        }

        @Override
        public Uni<List<OutboxEvent>> findPending(int limit) {
            lastRequestedLimit = limit;
            return Uni.createFrom().item(
                    pending.stream().limit(limit).toList());
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

    private static final class FakeEventPublisher implements EventPublisher {

        private final UUID failingEventId;
        private final List<UUID> publishedIds = new ArrayList<>();

        private FakeEventPublisher() {
            this(null);
        }

        private FakeEventPublisher(UUID failingEventId) {
            this.failingEventId = failingEventId;
        }

        @Override
        public Uni<Void> publish(OutboxEvent event) {
            publishedIds.add(event.eventId());

            if (event.eventId().equals(failingEventId)) {
                return Uni.createFrom()
                        .failure(new IllegalStateException("publication failed"));
            }

            return Uni.createFrom().voidItem();
        }
    }
}
