package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.acme.billing.application.event.VehicleRegistered;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EventJsonCodecTest {

    private final EventJsonCodec codec =
            new EventJsonCodec(new ObjectMapper().findAndRegisterModules());

    @Test
    void shouldDecodeEventFromJson() throws Exception {
        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                new VehicleRegistered.VehicleId(42L),
                "ABC123");

        ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();
        String payload = mapper.writeValueAsString(event);

        VehicleRegistered decoded = codec.decode(payload, VehicleRegistered.class);

        assertEquals(event, decoded);
    }

    @Test
    void shouldFailWhenPayloadCannotBeDeserialized() {
        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> codec.decode("not-json", VehicleRegistered.class));

        assertTrue(error.getMessage().contains("Could not deserialize VehicleRegistered event"));
    }
}
