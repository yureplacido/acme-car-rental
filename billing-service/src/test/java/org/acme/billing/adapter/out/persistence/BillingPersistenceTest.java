package org.acme.billing.adapter.out.persistence;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import jakarta.inject.Inject;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.InvoiceStatus;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Testes de persistência reais (Postgres via Dev Services). Usam {@link UniAsserter}
 * (sem transação envolvente) para que cada operação rode no escopo dos próprios
 * interceptores ({@code @WithSession}/{@code @WithTransaction}) através do proxy CDI —
 * a leitura após o save prova um commit genuíno, não o cache de primeira camada.
 */
@QuarkusTest
class BillingPersistenceTest {

    @Inject
    InvoiceRepository invoiceRepository;

    @Inject
    ProcessedEventStore processedEventStore;

    @Test
    @RunOnVertxContext
    void shouldPersistAndReloadInvoice(UniAsserter asserter) {
        String reservationId = "reservation-" + UUID.randomUUID();
        Invoice invoice = Invoice.draft(
                "alice",
                reservationId,
                List.of(new InvoiceLine("Rental", 2, Money.brl(new BigDecimal("100.00")))));

        asserter.<Invoice>assertThat(
                () -> invoiceRepository.save(invoice),
                saved -> assertNotNull(saved.id()));

        asserter.<Optional<Invoice>>assertThat(
                () -> invoiceRepository.findByReservationId(reservationId),
                loaded -> {
                    assertTrue(loaded.isPresent());
                    assertEquals("alice", loaded.get().customerId());
                    assertEquals(reservationId, loaded.get().reservationId());
                    assertEquals(new BigDecimal("200.00"), loaded.get().total().amount());
                });
    }

    @Test
    @RunOnVertxContext
    void shouldPersistOpenedStateAcrossTransactions(UniAsserter asserter) {
        String reservationId = "reservation-" + UUID.randomUUID();
        Invoice invoice = Invoice.draft(
                "alice",
                reservationId,
                List.of(new InvoiceLine("Rental", 1, Money.brl(new BigDecimal("100.00")))));

        asserter.<Invoice>assertThat(
                () -> invoiceRepository.save(invoice).map(Invoice::open)
                        .flatMap(invoiceRepository::save),
                saved -> assertEquals(InvoiceStatus.OPEN, saved.status()));

        asserter.<Optional<Invoice>>assertThat(
                () -> invoiceRepository.findByReservationId(reservationId),
                loaded -> assertEquals(InvoiceStatus.OPEN, loaded.map(Invoice::status).orElse(null)));
    }

    @Test
    @RunOnVertxContext
    void shouldClaimEventOnceAndRejectTheDuplicate(UniAsserter asserter) {
        UUID eventId = UUID.randomUUID();

        asserter.assertThat(
                () -> processedEventStore.tryClaim(eventId)
                        .flatMap(first -> processedEventStore.tryClaim(eventId)
                                .map(second -> new boolean[]{first, second})),
                results -> {
                    assertTrue(results[0]);
                    assertFalse(results[1]);
                });
    }

    @Test
    @RunOnVertxContext
    void shouldAllowReclaimAfterRelease(UniAsserter asserter) {
        UUID eventId = UUID.randomUUID();

        asserter.assertThat(
                () -> processedEventStore.tryClaim(eventId)
                        .flatMap(claimed -> processedEventStore.release(eventId))
                        .flatMap(ignored -> processedEventStore.tryClaim(eventId)),
                claimed -> assertTrue(claimed));
    }

    @Test
    @RunOnVertxContext
    void shouldEnforceSingleInvoicePerReservation(UniAsserter asserter) {
        String reservationId = "reservation-" + UUID.randomUUID();
        Invoice invoice = Invoice.draft(
                "alice",
                reservationId,
                List.of(new InvoiceLine("Rental", 1, Money.brl(new BigDecimal("100.00")))));

        asserter.assertFailedWith(
                () -> invoiceRepository.save(invoice)
                        .flatMap(ignored -> invoiceRepository.save(invoice)),
                Throwable.class);
    }
}