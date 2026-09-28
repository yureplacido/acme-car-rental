package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.EventPublisher;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.acme.billing.application.port.out.OutboxMetrics;

import java.time.Instant;
import java.util.List;

@ApplicationScoped
public class PublishPendingOutboxEvents {

    static final int DEFAULT_BATCH_SIZE = 100;

    private final OutboxEventStore outboxEventStore;
    private final EventPublisher eventPublisher;
    private final OutboxMetrics outboxMetrics;

    @Inject
    public PublishPendingOutboxEvents(
            OutboxEventStore outboxEventStore,
            EventPublisher eventPublisher,
            OutboxMetrics outboxMetrics) {
        this.outboxEventStore = outboxEventStore;
        this.eventPublisher = eventPublisher;
        this.outboxMetrics = outboxMetrics;
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
                .invoke(ignored -> outboxMetrics.eventRelayed())
                .onFailure()
                .call(ignored -> {
                    outboxMetrics.relayFailed();
                    return outboxEventStore.incrementAttempts(event);
                });
    }
}
