package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.EventPublisher;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishPendingOutboxEventsTest {

    @Test
    void shouldPublishPendingEventsAndMarkEachOneAsPublished() {
        OutboxEvent first = event("first");
        OutboxEvent second = event("second");
        FakeOutboxEventStore store = new FakeOutboxEventStore(List.of(first, second));
        FakeEventPublisher publisher = new FakeEventPublisher();

        PublishPendingOutboxEvents useCase =
                new PublishPendingOutboxEvents(store, publisher);

        useCase.handle().await().indefinitely();

        assertEquals(List.of(first.eventId(), second.eventId()), publisher.publishedIds);
        assertEquals(List.of(first.eventId(), second.eventId()), store.markedIds);
        assertTrue(store.incrementedIds.isEmpty());
    }

    @Test
    void shouldDoNothingWhenThereAreNoPendingEvents() {
        FakeOutboxEventStore store = new FakeOutboxEventStore(List.of());
        FakeEventPublisher publisher = new FakeEventPublisher();

        new PublishPendingOutboxEvents(store, publisher)
                .handle()
                .await()
                .indefinitely();

        assertTrue(publisher.publishedIds.isEmpty());
        assertTrue(store.markedIds.isEmpty());
        assertTrue(store.incrementedIds.isEmpty());
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

        Throwable failure = null;
        try {
            new PublishPendingOutboxEvents(store, publisher)
                    .handle()
                    .await()
                    .indefinitely();
        } catch (Throwable e) {
            failure = e;
        }

        assertTrue(failure != null);
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
    }

    @Test
    void shouldUseRequestedBatchSize() {
        OutboxEvent first = event("first");
        OutboxEvent second = event("second");
        FakeOutboxEventStore store = new FakeOutboxEventStore(List.of(first, second));
        FakeEventPublisher publisher = new FakeEventPublisher();

        new PublishPendingOutboxEvents(store, publisher)
                .handle(1)
                .await()
                .indefinitely();

        assertEquals(List.of(first.eventId()), publisher.publishedIds);
        assertEquals(1, store.lastRequestedLimit);
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
