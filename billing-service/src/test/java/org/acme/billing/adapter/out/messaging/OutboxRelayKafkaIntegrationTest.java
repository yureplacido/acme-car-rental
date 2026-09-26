package org.acme.billing.adapter.out.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.acme.billing.adapter.in.messaging.BillingKafkaCompanionResource;
import org.acme.billing.application.usecase.CreateInvoice;
import org.acme.billing.application.usecase.OpenInvoiceForRental;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class OutboxRelayKafkaIntegrationTest {

    private static final String TOPIC = "invoice-opened";
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    CreateInvoice createInvoice;

    @Inject
    OpenInvoiceForRental openInvoice;

    @Inject
    OutboxRelay relay;

    @Test
    @RunOnVertxContext
    void shouldPublishInvoiceOpenedFromOutboxToKafka(UniAsserter asserter) {
        Executor eventLoop = command -> Vertx.currentContext().runOnContext(ignored -> command.run());
        String reservationId = "outbox-kafka-" + UUID.randomUUID();
        AtomicReference<String> invoiceId = new AtomicReference<>();

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
                opened -> {
                    invoiceId.set(opened.id().value());
                    assertNotNull(invoiceId.get());
                });

        TopicPartition partition = KafkaCompanion.tp(TOPIC, 0);

        asserter.assertThat(
                () -> onWorkerThread(() -> companion.offsets().get(partition, OffsetSpec.latest()).offset())
                        .emitOn(eventLoop)
                        .flatMap(endOffset -> relay.relay().replaceWith(endOffset))
                        .flatMap(endOffset -> onWorkerThread(
                                () -> awaitInvoiceOpenedAfter(partition, endOffset, invoiceId.get()))),
                record -> {
                    assertEquals(invoiceId.get(), record.key(),
                            "the outbox record must be keyed by the invoice id");
                    assertNotNull(record.value());
                    assertTrue(record.value().contains(reservationId),
                            "the published payload must reference the invoice reservation: " + record.value());
                });
    }

    private <T> Uni<T> onWorkerThread(Supplier<T> blockingCall) {
        return Uni.createFrom().item(blockingCall)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private ConsumerRecord<String, String> awaitInvoiceOpenedAfter(
            TopicPartition partition, long fromOffset, String invoiceId) {
        return companion.consumeStrings()
                .fromOffsets(Map.of(partition, fromOffset),
                        records -> records.select().where(record -> invoiceId.equals(record.key())))
                .awaitRecords(1, PUBLISH_TIMEOUT)
                .getRecords()
                .get(0);
    }
}
