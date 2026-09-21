package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KafkaEventPublisherTest {

    @Test
    @SuppressWarnings("unchecked")
    void shouldPublishVehicleRegisteredWithVehicleIdAsKafkaKey() {
        var objectMapper = new ObjectMapper();
        MutinyEmitter<KafkaRecord<String, String>> emitter = mock(MutinyEmitter.class);

        when(emitter.send(any(KafkaRecord.class)))
                .thenReturn(Uni.createFrom().voidItem());

        var publisher = new KafkaEventPublisher(objectMapper, emitter);
        var event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        publisher.publish(event).await().indefinitely();

        var record = ArgumentCaptor.forClass(KafkaRecord.class);
        verify(emitter).send(record.capture());

        assertEquals("42", record.getValue().getKey());
        assertTrue(record.getValue().getValue().contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(record.getValue().getValue().contains("\"licensePlate\":\"ABC123\""));
    }
}
