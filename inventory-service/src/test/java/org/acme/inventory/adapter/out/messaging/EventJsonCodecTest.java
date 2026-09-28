package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventJsonCodecTest {

    private final EventJsonCodec codec =
            new EventJsonCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void shouldEncodeEventToJson() {
        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleId(42L),
                "ABC123");

        String payload = codec.encode(event);

        assertTrue(payload.contains("\"vehicleId\":{\"value\":42}"));
        assertTrue(payload.contains("\"licensePlate\":\"ABC123\""));
        assertTrue(payload.contains(event.eventId().toString()));
    }

    @Test
    void shouldFailWhenEventCannotBeSerialized() {
        Cyclic cyclic = new Cyclic();
        cyclic.self = cyclic;

        IllegalStateException error = assertThrows(
                IllegalStateException.class,
                () -> codec.encode(cyclic));

        assertTrue(error.getMessage().contains("Could not serialize Cyclic event"));
    }

    static class Cyclic {
        public Cyclic self;
    }
}
