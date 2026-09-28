package org.acme.inventory.adapter.out.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = InventoryKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class VehicleRegisteredEventPublisherIntegrationTest {

    private static final String TOPIC = "vehicle-registered";
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);

    @Inject
    VehicleRegisteredEventPublisher publisher;

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

        TopicPartition partition = KafkaCompanion.tp(TOPIC, 0);
        long fromOffset = companion.offsets().get(partition, OffsetSpec.latest()).offset();

        publisher.publish(event).await().indefinitely();

        ConsumerRecord<String, String> record = companion.consumeStrings()
                .fromOffsets(
                        Map.of(partition, fromOffset),
                        records -> records.select().where(
                                r -> r.value().contains(event.eventId().toString())))
                .awaitRecords(1, PUBLISH_TIMEOUT)
                .getRecords()
                .getFirst();

        assertEquals("42", record.key());

        String payload = record.value();
        assertTrue(payload.contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(payload.contains("\"licensePlate\":\"ABC123\""));
        assertTrue(payload.contains(event.eventId().toString()));
    }
}
