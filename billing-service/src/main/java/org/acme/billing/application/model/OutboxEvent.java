package org.acme.billing.application.model;

import java.time.Instant;
import java.util.UUID;

public record OutboxEvent(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        Instant occurredAt,
        int attempts) {
}
