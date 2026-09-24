package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class TransactionalInboxRetryTestConsumer {

    private final AtomicInteger attempts = new AtomicInteger();
    private final AtomicInteger successfulEffects = new AtomicInteger();
    private volatile UUID eventId;
    private CompletableFuture<Void> success = new CompletableFuture<>();

    private final InboundEventProcessor inboxProcessor;

    public TransactionalInboxRetryTestConsumer(InboundEventProcessor inboxProcessor) {
        this.inboxProcessor = inboxProcessor;
    }

    public void reset() {
        attempts.set(0);
        successfulEffects.set(0);
        eventId = null;
        success = new CompletableFuture<>();
    }

    @Incoming("transactional-inbox-retry-test-in")
    public Uni<Void> consume(Message<String> message) {
        UUID currentEventId = UUID.fromString(message.getPayload());
        eventId = currentEventId;
        int attempt = attempts.incrementAndGet();

        return inboxProcessor.process(
                currentEventId,
                () -> {
                    if (attempt == 1) {
                        return Uni.createFrom().failure(
                                new IllegalStateException("simulated business failure"));
                    }

                    successfulEffects.incrementAndGet();
                    success.complete(null);
                    return Uni.createFrom().voidItem();
                });
    }

    public CompletableFuture<Void> success() {
        return success;
    }

    public int attempts() {
        return attempts.get();
    }

    public int successfulEffects() {
        return successfulEffects.get();
    }

    public UUID eventId() {
        return eventId;
    }
}
