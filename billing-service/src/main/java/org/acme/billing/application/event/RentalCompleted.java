package org.acme.billing.application.event;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Evento de integração do bounded context de locação. Representação local
 * (anti-corrupção) — não compartilha tipos com rental-service.
 */
public record RentalCompleted(
        UUID eventId,
        int version,
        Instant occurredAt,
        String rentalId,
        String reservationId,
        String customerId,
        long vehicleId,
        String licensePlate,
        LocalDate startDate,
        LocalDate endDate,
        Money dailyRate) {

    public record Money(BigDecimal amount, String currency) {
    }
}