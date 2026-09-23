package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.reactive.messaging.memory.InMemoryConnector;
import io.smallrye.reactive.messaging.memory.InMemorySource;
import jakarta.inject.Inject;
import org.acme.billing.application.event.VehicleRegistered;
import org.eclipse.microprofile.reactive.messaging.spi.Connector;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(InMemoryMessagingTestResource.class)
class SubscriberDecoratorIntegrationTest {

    @Inject
    @Connector("smallrye-in-memory")
    InMemoryConnector connector;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    RecordingProcessedEventStore processedEventStore;

    @Inject
    NackTestConsumer nackTestConsumer;

    @BeforeEach
    void resetProcessedEventStore() {
        processedEventStore.reset();
    }

    @Test
    void shouldClaimOnlyOneDuplicateMessageBeforeItReachesTheConsumer()
            throws Exception {
        InMemorySource<String> source = connector.source("vehicle-registered-in");

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                "ABC123");

        String payload = objectMapper.writeValueAsString(event);

        source.send(payload);
        source.send(payload);

        processedEventStore.secondClaimAttempt()
                .get(5, TimeUnit.SECONDS);

        assertEquals(2, processedEventStore.claimAttempts());
    }

    @Test
    void shouldReleaseClaimWhenConsumerNacksAndAllowRedelivery()
            throws Exception {
        InMemorySource<String> source =
                connector.source("vehicle-registered-nack-test");

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(43L),
                "XYZ789");

        String payload = objectMapper.writeValueAsString(event);

        source.send(payload);

        nackTestConsumer.firstAttempt()
                .get(5, TimeUnit.SECONDS);

        source.send(payload);

        nackTestConsumer.secondAttempt()
                .get(5, TimeUnit.SECONDS);

        assertEquals(2, nackTestConsumer.attempts());
    }
}
