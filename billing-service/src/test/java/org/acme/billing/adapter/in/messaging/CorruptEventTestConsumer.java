package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.event.VehicleRegistered;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

@ApplicationScoped
public class CorruptEventTestConsumer {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger attempts = new AtomicInteger();
    private CompletableFuture<Void> exhausted = new CompletableFuture<>();
    private CompletableFuture<Void> unexpectedAttempt = new CompletableFuture<>();

    @Incoming("corrupt-test-in")
    public Uni<Void> consume(Message<String> message) {
        int attempt = attempts.incrementAndGet();

        if (attempt == 4) {
            exhausted.complete(null);
        }

        if (attempt > 4) {
            unexpectedAttempt.complete(null);
        }

        try {
            VehicleRegistered event = objectMapper.readValue(
                    message.getPayload(), VehicleRegistered.class);

            if (event.eventId() == null || event.vehicleId() == null) {
                return Uni.createFrom().completionStage(message.nack(
                        new IllegalArgumentException("Corrupt VehicleRegistered event")));
            }

            return Uni.createFrom().voidItem();
        } catch (JsonProcessingException e) {
            return Uni.createFrom().completionStage(message.nack(
                    new IllegalArgumentException("Corrupt VehicleRegistered event", e)));
        }
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