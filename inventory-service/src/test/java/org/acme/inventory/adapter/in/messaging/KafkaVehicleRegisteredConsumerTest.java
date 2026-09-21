package org.acme.inventory.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.acme.inventory.application.port.out.ProcessedEventStore;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

class KafkaVehicleRegisteredConsumerTest {

    @Test
    void shouldDeserializeAndProcessVehicleRegisteredEvent() {
        ProcessedEventStore store = new InMemoryProcessedEventStore();
        KafkaVehicleRegisteredConsumer consumer =
                new KafkaVehicleRegisteredConsumer(new ObjectMapper().findAndRegisterModules(), store);

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        String payload = assertDoesNotThrow(() -> new ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsString(event));

        assertDoesNotThrow(() -> consumer.consume(payload).await().indefinitely());
    }

    @Test
    void shouldIgnoreTheSameKafkaEventWhenItIsDeliveredAgain() throws Exception {
        ProcessedEventStore store = new InMemoryProcessedEventStore();
        KafkaVehicleRegisteredConsumer consumer =
                new KafkaVehicleRegisteredConsumer(new ObjectMapper().findAndRegisterModules(), store);

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
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
        public synchronized Uni<Boolean> markIfNew(UUID eventId) {
            return Uni.createFrom().item(processed.add(eventId));
        }

        int size() {
            return processed.size();
        }
    }
}
