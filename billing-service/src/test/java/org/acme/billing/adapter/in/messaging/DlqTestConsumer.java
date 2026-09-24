package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class DlqTestConsumer {

    private final AtomicInteger attempts = new AtomicInteger();
    private CompletableFuture<Void> exhausted = new CompletableFuture<>();
    private CompletableFuture<Void> unexpectedAttempt = new CompletableFuture<>();

    @Incoming("dlq-test-in")
    public Uni<Void> consume(Message<String> message) {
        int attempt = attempts.incrementAndGet();

        if (attempt == 4) {
            exhausted.complete(null);
        }

        if (attempt > 4) {
            unexpectedAttempt.complete(null);
        }

        return Uni.createFrom()
                .completionStage(message.nack(
                        new IllegalArgumentException("VehicleRegistered event is missing required fields")));
    }

    public void reset() {
        attempts.set(0);
        exhausted = new CompletableFuture<>();
        unexpectedAttempt = new CompletableFuture<>();
    }

    public CompletableFuture<Void> exhausted() {
        return exhausted;
    }

    public CompletableFuture<Void> unexpectedAttempt() {
        return unexpectedAttempt;
    }

    public int attempts() {
        return attempts.get();
    }
}