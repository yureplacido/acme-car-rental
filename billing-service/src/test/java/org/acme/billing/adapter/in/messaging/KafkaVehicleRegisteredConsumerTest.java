package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.billing.application.event.VehicleRegistered;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class KafkaVehicleRegisteredConsumerTest {

    @Test
    void shouldDeserializeAndProcessVehicleRegisteredEvent() throws Exception {
        KafkaVehicleRegisteredConsumer consumer =
                new KafkaVehicleRegisteredConsumer(
                        new ObjectMapper().findAndRegisterModules());

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
}
