package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class DelayedRetryTestConsumer {

    private final AtomicInteger attempts = new AtomicInteger();
    private final CompletableFuture<Void> thirdAttempt = new CompletableFuture<>();
    private final CompletableFuture<Void> firstAttempt = new CompletableFuture<>();
    private final CompletableFuture<Void> secondAttempt = new CompletableFuture<>();
    private final CompletableFuture<Void> success = new CompletableFuture<>();
    private volatile Instant firstAttemptAt;
    private volatile Instant secondAttemptAt;
    private volatile Instant thirdAttemptAt;

    @Incoming("retry-test-in")
    public Uni<Void> consume(Message<String> message) {
        int attempt = attempts.incrementAndGet();
        Instant now = Instant.now();

        if (attempt == 1) {
            firstAttemptAt = now;
            firstAttempt.complete(null);
            return nack(message, "simulated failure 1");
        }

        if (attempt == 2) {
            secondAttemptAt = now;
            secondAttempt.complete(null);
            return nack(message, "simulated failure 2");
        }

        if (attempt == 3) {
            thirdAttemptAt = now;
            thirdAttempt.complete(null);
            success.complete(null);
        }

        return Uni.createFrom().voidItem();
    }

    private Uni<Void> nack(Message<String> message, String reason) {
        return Uni.createFrom()
                .completionStage(message.nack(new IllegalStateException(reason)));
    }

    public CompletableFuture<Void> firstAttempt() {
        return firstAttempt;
    }

    public CompletableFuture<Void> secondAttempt() {
        return secondAttempt;
    }

    public CompletableFuture<Void> thirdAttempt() {
        return thirdAttempt;
    }

    public CompletableFuture<Void> success() {
        return success;
    }

    public int attempts() {
        return attempts.get();
    }

    public Instant firstAttemptAt() {
        return firstAttemptAt;
    }

    public Instant secondAttemptAt() {
        return secondAttemptAt;
    }

    public Instant thirdAttemptAt() {
        return thirdAttemptAt;
    }
}
