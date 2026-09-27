package org.acme.inventory.adapter.out.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = InventoryKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class KafkaEventPublisherIntegrationTest {

    @Inject
    KafkaEventPublisher publisher;

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Test
    void shouldPublishVehicleRegisteredToRealKafkaBrokerWithVehicleIdAsKey() {
        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        publisher.publish(event).await().indefinitely();

        var records = companion.consumeStrings()
                .fromTopics("vehicle-registered", 1, Duration.ofSeconds(10))
                .awaitRecords(1)
                .getRecords();

        assertEquals(1, records.size());
        assertEquals("42", records.get(0).key());

        String payload = records.get(0).value();
        assertTrue(payload.contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(payload.contains("\"licensePlate\":\"ABC123\""));
        assertTrue(payload.contains(event.eventId().toString()));
    }
}
