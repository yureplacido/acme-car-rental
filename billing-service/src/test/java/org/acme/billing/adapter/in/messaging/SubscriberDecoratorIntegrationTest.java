package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import org.acme.billing.application.event.VehicleRegistered;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;

import static org.awaitility.Awaitility.await;
import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(InMemoryMessagingTestResource.class)
class SubscriberDecoratorIntegrationTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    @Test
    void shouldClaimOnlyOneDuplicateMessageBeforeItReachesTheConsumer() throws Exception {
        InMemorySource<String> source = connector.source("vehicle-registered-in");
        AtomicInteger acknowledgements = new AtomicInteger();

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                "ABC123");

        String payload = objectMapper.writeValueAsString(event);

        source.send(message(payload, acknowledgements));
        source.send(message(payload, acknowledgements));

        await().untilAsserted(() -> assertEquals(1, acknowledgements.get()));
    }

    private static Message<String> message(
            String payload,
            AtomicInteger acknowledgements) {
        return Message.of(
                payload,
                () -> {
                    acknowledgements.incrementAndGet();
                    return CompletableFuture.completedFuture(null);
                });
    }
}
