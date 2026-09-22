package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaEventPublisherTest {

    @Test
    void shouldPublishVehicleRegisteredWithVehicleIdAsKafkaKey() {
        RecordingEmitter emitter = new RecordingEmitter();
        KafkaEventPublisher publisher =
                new KafkaEventPublisher(new ObjectMapper().findAndRegisterModules(), emitter);

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        publisher.publish(event).await().indefinitely();

        assertNotNull(emitter.sentRecord);
        assertEquals("42", emitter.sentRecord.getKey());

        String payload = emitter.sentRecord.getPayload();
        assertTrue(payload.contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(payload.contains("\"licensePlate\":\"ABC123\""));
    }

    static class RecordingEmitter implements MutinyEmitter<KafkaRecord<String, String>> {

        KafkaRecord<String, String> sentRecord;

        @Override
        public Uni<Void> send(KafkaRecord<String, String> payload) {
            sentRecord = payload;
            return Uni.createFrom().voidItem();
        }

        @Override
        public void sendAndAwait(KafkaRecord<String, String> payload) {
            send(payload).await().indefinitely();
        }

        @Override
        public Cancellable sendAndForget(KafkaRecord<String, String> payload) {
            send(payload).subscribe().with(ignored -> {
            });
            return () -> {
            };
        }

        @Override
        public <M extends Message<? extends KafkaRecord<String, String>>> void send(M message) {
            throw new UnsupportedOperationException("not used by KafkaEventPublisher");
        }

        @Override
        public <M extends Message<? extends KafkaRecord<String, String>>> Uni<Void> sendMessage(M message) {
            throw new UnsupportedOperationException("not used by KafkaEventPublisher");
        }

        @Override
        public <M extends Message<? extends KafkaRecord<String, String>>> void sendMessageAndAwait(M message) {
            throw new UnsupportedOperationException("not used by KafkaEventPublisher");
        }

        @Override
        public <M extends Message<? extends KafkaRecord<String, String>>> Cancellable sendMessageAndForget(M message) {
            throw new UnsupportedOperationException("not used by KafkaEventPublisher");
        }

        @Override
        public void complete() {
        }

        @Override
        public void error(Exception e) {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }

        @Override
        public boolean hasRequests() {
            return true;
        }
    }
}