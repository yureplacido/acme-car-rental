package org.acme.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * Evento de integração do bounded context de reserva. Este registro é a
 * representação local (anti-corrupção) — não compartilha tipos com reservation-service.
 */
public record ReservationConfirmed(
        UUID eventId,
        int version,
        Instant occurredAt,
        String reservationId,
        String customerId,
        long vehicleId,
        String licensePlate,
        String from,
        String to,
        Money dailyRate) {

    public record Money(BigDecimal amount, String currency) {
    }
}