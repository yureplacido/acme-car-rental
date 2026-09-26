package org.acme.billing.adapter.out.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.inject.Inject;
import org.acme.billing.adapter.in.messaging.BillingFlowKafkaCompanionResource;
import org.acme.billing.application.usecase.CreateInvoice;
import org.acme.billing.application.usecase.OpenInvoiceForRental;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@QuarkusTest
@QuarkusTestResource(BillingFlowKafkaCompanionResource.class)
class OutboxRelayKafkaIntegrationTest {

    private static final String TOPIC = "invoice-opened";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    CreateInvoice createInvoice;

    @Inject
    OpenInvoiceForRental openInvoice;

    @Inject
    OutboxRelay relay;

    @Inject
    PgPool pgPool;

    @BeforeEach
    void setUp() {
        companion.topics().clear(TOPIC);
        pgPool.query("delete from outbox_event where published_at is null")
                .executeAndAwait();
    }

    @Test
    @RunOnVertxContext
    void shouldPublishInvoiceOpenedFromOutboxToKafka(UniAsserter asserter) {
        String reservationId = "outbox-kafka-" + UUID.randomUUID();

        CreateInvoice.Command create = new CreateInvoice.Command(
                "customer-1",
                reservationId,
                List.of(InvoiceLine.rentalDays(
                        "Aluguel de veículo ABC-1234",
                        LocalDate.of(2026, 9, 25),
                        LocalDate.of(2026, 9, 27),
                        new Money(new BigDecimal("100.00"), "BRL"))));

        asserter.assertThat(
                () -> createInvoice.handle(create)
                        .flatMap(invoice -> openInvoice.handle(
                                new OpenInvoiceForRental.Command(
                                        new OpenInvoiceForRental.RentalDetails(
                                                reservationId,
                                                LocalDate.of(2026, 9, 26),
                                                LocalDate.of(2026, 9, 28),
                                                new Money(new BigDecimal("120.00"), "BRL"),
                                                "ABC-1234")))),
                opened -> assertNotNull(opened.id()));

        asserter.execute(() -> relay.relay());

        asserter.assertThat(
                () -> io.smallrye.mutiny.Uni.createFrom()
                        .item(() -> companion.consumeStrings()
                                .fromTopics(TOPIC, 1, Duration.ofSeconds(5))
                                .awaitRecords(1)
                                .getRecords()
                                .get(0))
                        .runSubscriptionOn(io.smallrye.mutiny.infrastructure.Infrastructure.getDefaultExecutor()),
                record -> {
                    assertEquals(reservationId, record.key());
                    assertNotNull(record.value());
                });
    }
}
