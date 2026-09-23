package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class DelayedRetryExhaustionTestConsumer {

    private final AtomicInteger attempts = new AtomicInteger();
    private final CompletableFuture<Void> exhausted = new CompletableFuture<>();

    @Incoming("retry-exhaustion-in")
    public Uni<Void> consume(Message<String> message) {
        int attempt = attempts.incrementAndGet();

        if (attempt == 4) {
            exhausted.complete(null);
        }

        return Uni.createFrom()
                .completionStage(message.nack(
                        new IllegalStateException("simulated permanent failure")));
    }

    public CompletableFuture<Void> exhausted() {
        return exhausted;
    }

    public int attempts() {
        return attempts.get();
    }
}
