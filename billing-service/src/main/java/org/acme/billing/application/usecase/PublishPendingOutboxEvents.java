package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.EventPublisher;
import org.acme.billing.application.port.out.OutboxEventStore;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class PublishPendingOutboxEvents {

    static final int DEFAULT_BATCH_SIZE = 100;

    private final OutboxEventStore outboxEventStore;
    private final EventPublisher eventPublisher;

    public PublishPendingOutboxEvents(
            OutboxEventStore outboxEventStore,
            EventPublisher eventPublisher) {
        this.outboxEventStore = outboxEventStore;
        this.eventPublisher = eventPublisher;
    }

    public Uni<Void> handle() {
        return handle(DEFAULT_BATCH_SIZE);
    }

    public Uni<Void> handle(int batchSize) {
        return outboxEventStore.findPending(batchSize)
                .flatMap(this::publishSequentially);
    }

    private Uni<Void> publishSequentially(List<OutboxEvent> events) {
        return Multi.createFrom().iterable(events)
                .onItem().transformToUniAndConcatenate(this::publishOne)
                .collect().last()
                .replaceWithVoid();
    }

    private Uni<Void> publishOne(OutboxEvent event) {
        return eventPublisher.publish(event)
                .flatMap(ignored -> outboxEventStore.markPublished(event, Instant.now()))
                .onFailure()
                .call(ignored -> outboxEventStore.incrementAttempts(event));
    }
}
