package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class NackTestConsumer {

    private final AtomicInteger attempts = new AtomicInteger();
    private final CompletableFuture<Void> firstAttempt = new CompletableFuture<>();
    private final CompletableFuture<Void> secondAttempt = new CompletableFuture<>();

    @Incoming("vehicle-registered-nack-test")
    public Uni<Void> consume(Message<String> message) {
        int attempt = attempts.incrementAndGet();

        if (attempt == 1) {
            firstAttempt.complete(null);
            return Uni.createFrom()
                    .failure(new IllegalStateException("simulated processing failure"));
        }

        if (attempt == 2) {
            secondAttempt.complete(null);
        }

        return Uni.createFrom().voidItem();
    }

    public CompletableFuture<Void> firstAttempt() {
        return firstAttempt;
    }

    public CompletableFuture<Void> secondAttempt() {
        return secondAttempt;
    }

    public int attempts() {
        return attempts.get();
    }
}
