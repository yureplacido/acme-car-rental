package org.acme.billing.adapter.in.messaging;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.port.out.ProcessedEventStore;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * Transaction boundary for inbound event processing.
 *
 * The inbox claim and the business effect deliberately execute in the same
 * reactive transaction. A failure rolls back both, allowing the broker retry
 * to claim the event again.
 */
@ApplicationScoped
public class TransactionalInboxProcessor implements InboundEventProcessor {

    private final ProcessedEventStore processedEventStore;

    @Inject
    public TransactionalInboxProcessor(ProcessedEventStore processedEventStore) {
        this.processedEventStore = processedEventStore;
    }

    @Override
    @WithTransaction
    public Uni<Void> process(UUID eventId, Supplier<Uni<Void>> businessEffect) {
        return processedEventStore.tryClaim(eventId)
                .flatMap(claimed -> claimed
                        ? businessEffect.get()
                        : Uni.createFrom().voidItem());
    }
}
