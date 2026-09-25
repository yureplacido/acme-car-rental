package org.acme.billing.adapter.out.persistence;

import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.inject.Inject;
import org.acme.billing.application.usecase.CreateInvoice;
import org.acme.billing.application.usecase.OpenInvoiceForRental;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
class BillingOutboxIntegrationTest {

    @Inject
    CreateInvoice createInvoice;

    @Inject
    OpenInvoiceForRental openInvoice;

    @Inject
    PgPool pgPool;

    @Test
    void shouldCommitInvoiceAndOutboxTogetherWhenInvoiceIsOpened() {
        String reservationId = "outbox-" + UUID.randomUUID();

        CreateInvoice.Command create = new CreateInvoice.Command(
                "customer-1",
                reservationId,
                List.of(InvoiceLine.rentalDays(
                        "Aluguel de veículo ABC-1234",
                        LocalDate.of(2026, 9, 25),
                        LocalDate.of(2026, 9, 27),
                        new Money(new BigDecimal("100.00"), "BRL"))));

        createInvoice.handle(create)
                .flatMap(invoice -> openInvoice.handle(
                        new OpenInvoiceForRental.Command(
                                new OpenInvoiceForRental.RentalDetails(
                                        reservationId,
                                        LocalDate.of(2026, 9, 26),
                                        LocalDate.of(2026, 9, 28),
                                        new Money(new BigDecimal("120.00"), "BRL"),
                                        "ABC-1234"))))
                .await().indefinitely();

        Row row = pgPool.withConnection(connection ->
                connection.preparedQuery("""
                                SELECT i.status,
                                       o.event_id,
                                       o.event_type,
                                       o.aggregate_id,
                                       o.published_at,
                                       o.attempts
                                FROM invoice i
                                JOIN outbox_event o
                                  ON o.aggregate_id = i.id::text
                                WHERE i.reservation_id = $1
                                """)
                        .execute(io.vertx.mutiny.sqlclient.Tuple.of(reservationId))
                        .map(rows -> {
                            var iterator = rows.iterator();
                            if (!iterator.hasNext()) {
                                return null;
                            }
                            var item = iterator.next();
                            return new Row(
                                    item.getString("status"),
                                    item.getString("event_id"),
                                    item.getString("event_type"),
                                    item.getString("aggregate_id"),
                                    item.getInstant("published_at"),
                                    item.getInteger("attempts"));
                        }))
                .await().indefinitely();

        assertNotNull(row);
        assertEquals("OPEN", row.status());
        assertNotNull(row.eventId());
        assertEquals("InvoiceOpened", row.eventType());
        assertNotNull(row.aggregateId());
        assertEquals(null, row.publishedAt());
        assertEquals(0, row.attempts());
    }

    private record Row(
            String status,
            String eventId,
            String eventType,
            String aggregateId,
            java.time.Instant publishedAt,
            Integer attempts) {
    }
}
