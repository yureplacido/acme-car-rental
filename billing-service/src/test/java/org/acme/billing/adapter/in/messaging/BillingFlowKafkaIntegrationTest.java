package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.acme.billing.application.event.ReservationConfirmed;
import org.acme.billing.application.event.RentalCompleted;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceStatus;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(BillingFlowKafkaCompanionResource.class)
class BillingFlowKafkaIntegrationTest {

    private static final String RESERVATION_CONFIRMED_TOPIC = "reservation-confirmed";
    private static final String RENTAL_COMPLETED_TOPIC = "rental-completed";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    InvoiceRepository invoiceRepository;

    @Test
    @RunOnVertxContext
    void shouldCreateDraftInvoiceThenOpenItOnRentalCompletion(UniAsserter asserter) {
        io.vertx.core.Context context = io.vertx.core.Vertx.currentContext();
        String reservationId = "reservation-flow-" + UUID.randomUUID();
        ReservationConfirmed confirmed = new ReservationConfirmed(
                UUID.randomUUID(),
                1,
                Instant.now(),
                reservationId,
                "alice",
                7L,
                "FLOW-1",
                "2026-11-01",
                "2026-11-03",
                new ReservationConfirmed.Money(new BigDecimal("200.00"), "BRL"));

        asserter.execute(() -> produceRecord(context, RESERVATION_CONFIRMED_TOPIC, reservationId, writeValue(confirmed)));

        asserter.assertThat(
                () -> awaitInvoiceStatus(reservationId, InvoiceStatus.DRAFT)
                        .flatMap(status -> Uni.createFrom().deferred(
                                () -> invoiceRepository.findByReservationId(reservationId))),
                invoice -> {
                    assertNotNull(invoice.orElse(null));
                    assertEquals(InvoiceStatus.DRAFT, invoice.get().status());
                    assertEquals(new BigDecimal("600.00"), invoice.get().total().amount());
                });

        RentalCompleted completed = new RentalCompleted(
                UUID.randomUUID(),
                1,
                Instant.now(),
                "rental-flow",
                reservationId,
                "alice",
                7L,
                "FLOW-1",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 3),
                new RentalCompleted.Money(new BigDecimal("200.00"), "BRL"));

        asserter.execute(() -> produceRecord(context, RENTAL_COMPLETED_TOPIC, reservationId, writeValue(completed)));

        asserter.assertThat(
                () -> awaitInvoiceStatus(reservationId, InvoiceStatus.OPEN),
                status -> assertEquals(InvoiceStatus.OPEN, status));
    }

    @Test
    @RunOnVertxContext
    void shouldIgnoreDuplicateReservationConfirmedEvent(UniAsserter asserter) {
        io.vertx.core.Context context = io.vertx.core.Vertx.currentContext();
        String reservationId = "reservation-dup-" + UUID.randomUUID();
        ReservationConfirmed confirmed = new ReservationConfirmed(
                UUID.randomUUID(),
                1,
                Instant.now(),
                reservationId,
                "alice",
                8L,
                "DUP-1",
                "2026-11-05",
                "2026-11-06",
                new ReservationConfirmed.Money(new BigDecimal("100.00"), "BRL"));

        String payload = writeValue(confirmed);
        asserter.execute(() -> produceRecord(context, RESERVATION_CONFIRMED_TOPIC, reservationId, payload));
        asserter.execute(() -> produceRecord(context, RESERVATION_CONFIRMED_TOPIC, reservationId, payload));

        asserter.assertThat(
                () -> awaitInvoiceStatus(reservationId, InvoiceStatus.DRAFT),
                status -> assertEquals(InvoiceStatus.DRAFT, status));

        asserter.assertThat(
                () -> Uni.createFrom().deferred(() -> invoiceRepository.findByReservationId(reservationId)),
                invoice -> assertTrue(invoice.isPresent()));
    }

    private Uni<Void> produceRecord(io.vertx.core.Context context, String topic, String key, String payload) {
        Executor executor = command -> context.runOnContext(ignored -> command.run());
        return Uni.createFrom()
                .item(new ProducerRecord<>(topic, key, payload))
                .runSubscriptionOn(Infrastructure.getDefaultExecutor())
                .map(record -> {
                    produceBlocking(record);
                    return null;
                })
                .replaceWithVoid()
                .emitOn(executor);
    }

    private void produceBlocking(ProducerRecord<String, String> record) {
        companion.produceStrings()
                .fromRecords(record)
                .awaitCompletion();
    }

    private Uni<InvoiceStatus> awaitInvoiceStatus(String reservationId, InvoiceStatus expected) {
        return Uni.createFrom().deferred(() -> invoiceRepository.findByReservationId(reservationId))
                .map(maybe -> maybe.map(Invoice::status).orElse(null))
                .flatMap(status -> {
                    if (expected.equals(status)) {
                        return Uni.createFrom().item(status);
                    }
                    return Uni.createFrom().failure(
                            new TimeoutException("invoice still " + status + " for " + reservationId));
                })
                .onFailure().retry()
                .withBackOff(Duration.ofMillis(100), Duration.ofMillis(500))
                .withJitter(0.5)
                .atMost(30);
    }

    private String writeValue(Object event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (Exception e) {
            throw new IllegalArgumentException("Could not serialize event", e);
        }
    }
}