package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class KafkaEventPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldPublishVehicleRegisteredWithVehicleIdAsKafkaKey() {
        var objectMapper = new ObjectMapper();
        MutinyEmitter<KafkaRecord<String, String>> emitter = mock(MutinyEmitter.class);
        var sentRecord = new AtomicReference<KafkaRecord<String, String>>();

        when(emitter.send((KafkaRecord<String, String>) any(KafkaRecord.class)))
                .thenAnswer(invocation -> {
                    sentRecord.set(invocation.getArgument(0));
                    return Uni.createFrom().voidItem();
                });

        var publisher = new KafkaEventPublisher(objectMapper, emitter);
        var event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        publisher.publish(event).await().indefinitely();

        var record = sentRecord.get();

        assertEquals("42", record.getKey());
        assertTrue(record.getPayload().contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(record.getPayload().contains("\"licensePlate\":\"ABC123\""));
    }
}
