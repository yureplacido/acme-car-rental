package org.acme.billing.application;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.application.usecase.CreateInvoice;
import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class CreateInvoiceTest {

    @Test
    void shouldCreateDraftInvoiceThroughRepositoryPort() {
        FakeRepository repository = new FakeRepository();
        CreateInvoice useCase = new CreateInvoice(repository);

        Invoice invoice = useCase.handle(new CreateInvoice.Command(
                "alice",
                "reservation-42",
                List.of(new InvoiceLine("Rental", 1, Money.brl(new BigDecimal("100.00"))))))
                .await().atMost(Duration.ofSeconds(5));

        assertEquals("alice", invoice.customerId());
        assertEquals("reservation-42", invoice.reservationId());
        assertEquals(new BigDecimal("100.00"), invoice.total().amount());
        assertSame(invoice, repository.saved);
    }

    static class FakeRepository implements InvoiceRepository {
        Invoice saved;
        public Uni<Invoice> save(Invoice invoice) { saved = invoice; return Uni.createFrom().item(invoice); }
        public Uni<Optional<Invoice>> findByReservationId(String reservationId) {
            return Uni.createFrom().item(Optional.ofNullable(saved));
        }
    }
}
