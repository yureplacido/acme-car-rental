package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Codec JSON dos eventos de integração emitidos pelo Inventory.
 * Centraliza a serialização com o ObjectMapper do Quarkus,
 * evitando try/catch duplicado em cada adapter de mensageria.
 */
@ApplicationScoped
public class EventJsonCodec {

    private final ObjectMapper objectMapper;

    public EventJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public String encode(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(
                    "Could not serialize " + event.getClass().getSimpleName() + " event", e);
        }
    }
}
