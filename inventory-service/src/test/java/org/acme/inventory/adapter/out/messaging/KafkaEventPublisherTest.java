package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;
import org.mockito.invocation.InvocationOnMock;

import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

class KafkaEventPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldPublishVehicleRegisteredWithVehicleIdAsKafkaKey() {
        var objectMapper = new ObjectMapper();
        var sentRecord = new AtomicReference<KafkaRecord<String, String>>();

        MutinyEmitter<KafkaRecord<String, String>> emitter = mock(
                MutinyEmitter.class,
                invocation -> handleSend(invocation, sentRecord));

        var publisher = new KafkaEventPublisher(objectMapper, emitter);
        var event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        publisher.publish(event).await().indefinitely();

        var record = sentRecord.get();
        var payload = (String) record.getPayload();

        assertEquals("42", record.getKey());
        assertTrue(payload.contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(payload.contains("\"licensePlate\":\"ABC123\""));
    }

    private static Object handleSend(
            InvocationOnMock invocation,
            AtomicReference<KafkaRecord<String, String>> sentRecord) {

        if (invocation.getArguments().length == 1
                && invocation.getArgument(0) instanceof KafkaRecord<?, ?> kafkaRecord) {
            sentRecord.set((KafkaRecord<String, String>) kafkaRecord);
            return Uni.createFrom().voidItem();
        }

        return null;
    }
}
