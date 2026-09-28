package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Codec JSON dos eventos de integração recebidos pelo Billing.
 * Centraliza a deserialização com o ObjectMapper do Quarkus,
 * evitando try/catch duplicado em cada consumer de mensageria.
 */
@ApplicationScoped
public class EventJsonCodec {

    private final ObjectMapper objectMapper;

    public EventJsonCodec(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public <T> T decode(String payload, Class<T> type) {
        try {
            return objectMapper.readValue(payload, type);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Could not deserialize " + type.getSimpleName() + " event", e);
        }
    }
}
