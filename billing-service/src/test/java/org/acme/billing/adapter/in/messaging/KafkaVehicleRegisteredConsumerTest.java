package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.billing.application.event.VehicleRegistered;
import org.acme.billing.application.port.out.ProcessedEventStore;
import io.smallrye.mutiny.Uni;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class KafkaVehicleRegisteredConsumerTest {

    @Test
    void shouldDeserializeAndProcessVehicleRegisteredEvent() throws Exception {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        KafkaVehicleRegisteredConsumer consumer =
                new KafkaVehicleRegisteredConsumer(
                        new ObjectMapper().findAndRegisterModules(),
                        store);

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                "ABC123");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(event);

        assertDoesNotThrow(() ->
                consumer.consume(payload).await().indefinitely());
    }

    @Test
    void shouldIgnoreTheSameKafkaEventWhenItIsDeliveredAgain() throws Exception {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        KafkaVehicleRegisteredConsumer consumer =
                new KafkaVehicleRegisteredConsumer(
                        new ObjectMapper().findAndRegisterModules(),
                        store);

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                "ABC123");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(event);

        consumer.consume(payload).await().indefinitely();
        consumer.consume(payload).await().indefinitely();

        assertEquals(1, store.size());
    }

    static class InMemoryProcessedEventStore implements ProcessedEventStore {
        private final Set<UUID> processed = new HashSet<>();

        @Override
        public synchronized Uni<Boolean> isProcessed(UUID eventId) {
            return Uni.createFrom().item(processed.contains(eventId));
        }

        @Override
        public synchronized Uni<Void> markProcessed(UUID eventId) {
            processed.add(eventId);
            return Uni.createFrom().voidItem();
        }

        int size() {
            return processed.size();
        }
    }
}
