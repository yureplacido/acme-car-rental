package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.VehicleRegistered;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaVehicleRegisteredConsumerTest {

    @Test
    void shouldConsumeWellFormedVehicleRegisteredEventPayload() throws Exception {
        AtomicReference<VehicleRegistered> received = new AtomicReference<>();
        KafkaVehicleRegisteredConsumer consumer =
                new KafkaVehicleRegisteredConsumer(
                        new ObjectMapper().findAndRegisterModules(),
                        event -> {
                            received.set(event);
                            return Uni.createFrom().voidItem();
                        });

        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                "ABC123");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(event);

        consumer.consume(payload).await().atMost(Duration.ofSeconds(5));

        assertEquals(event, received.get());
    }

    @Test
    void shouldFailWhenPayloadCannotBeDeserialized() {
        KafkaVehicleRegisteredConsumer consumer =
                new KafkaVehicleRegisteredConsumer(
                        new ObjectMapper().findAndRegisterModules());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume("not-json").await().atMost(Duration.ofSeconds(5)));

        assertTrue(error.getMessage().contains("Could not deserialize VehicleRegistered event"));
    }
}