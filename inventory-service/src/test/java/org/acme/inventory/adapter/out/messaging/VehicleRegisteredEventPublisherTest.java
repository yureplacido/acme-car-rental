package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.subscription.Cancellable;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VehicleRegisteredEventPublisherTest {

    @Test
    void shouldPublishVehicleRegisteredWithVehicleIdAsKafkaKey() {
        RecordingEmitter emitter = new RecordingEmitter();
        VehicleRegisteredEventPublisher publisher =
                new VehicleRegisteredEventPublisher(
                        new EventJsonCodec(new ObjectMapper().findAndRegisterModules()),
                        emitter);

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        publisher.publish(event).await().indefinitely();

        assertNotNull(emitter.sentMessage);

        Record<String, String> record = emitter.sentMessage.getPayload();
        assertEquals("42", record.key());

        String payload = record.value();
        assertTrue(payload.contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(payload.contains("\"licensePlate\":\"ABC123\""));
    }

    @Test
    void shouldDeliverSerializationFailureAsFailedUni() {
        RecordingEmitter emitter = new RecordingEmitter();
        VehicleRegisteredEventPublisher publisher =
                new VehicleRegisteredEventPublisher(
                        new FailingCodec(),
                        emitter);

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        Uni<Void> result = publisher.publish(event);

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> result.await().indefinitely());

        assertTrue(error.getMessage().contains("boom"));
    }

    static class FailingCodec extends EventJsonCodec {

        FailingCodec() {
            super(new ObjectMapper());
        }

        @Override
        public String encode(Object event) {
            throw new IllegalStateException("boom");
        }
    }

    static class RecordingEmitter implements MutinyEmitter<Record<String, String>> {

        Message<Record<String, String>> sentMessage;

        @Override
        public Uni<Void> send(Record<String, String> payload) {
            sentMessage = Message.of(payload);
            return Uni.createFrom().voidItem();
        }

        @Override
        public void sendAndAwait(Record<String, String> payload) {
            send(payload).await().indefinitely();
        }

        @Override
        public Cancellable sendAndForget(Record<String, String> payload) {
            send(payload).subscribe().with(ignored -> {
            });
            return () -> {
            };
        }

        @Override
        public <M extends Message<? extends Record<String, String>>> void send(M message) {
            sentMessage = Message.of(message.getPayload());
        }

        @Override
        public <M extends Message<? extends Record<String, String>>> Uni<Void> sendMessage(M message) {
            sentMessage = copyMessage(message);
            return Uni.createFrom().voidItem();
        }

        @Override
        public <M extends Message<? extends Record<String, String>>> void sendMessageAndAwait(M message) {
            sentMessage = copyMessage(message);
        }

        @Override
        public <M extends Message<? extends Record<String, String>>> Cancellable sendMessageAndForget(M message) {
            sentMessage = copyMessage(message);
            return () -> {
            };
        }

        private Message<Record<String, String>> copyMessage(
                Message<? extends Record<String, String>> message) {
            return Message.of(message.getPayload(), message.getMetadata());
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
