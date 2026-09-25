package org.acme.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de integração emitido pelo Billing quando uma fatura é aberta.
 * O eventId permanece estável no outbox para permitir publicação at-least-once
 * e deduplicação no consumidor.
 */
public record InvoiceOpened(
        UUID eventId,
        int version,
        Instant occurredAt,
        String invoiceId,
        String customerId,
        String reservationId,
        BigDecimal totalAmount,
        String currency) {
}
