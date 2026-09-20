package org.acme.billing.application;

import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.application.usecase.CreateInvoice;
import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CreateInvoiceTest {

    @Test
    void shouldCreateDraftInvoiceThroughRepositoryPort() {
        FakeRepository repository = new FakeRepository();
        CreateInvoice useCase = new CreateInvoice(repository);

        Invoice invoice = useCase.handle(new CreateInvoice.Command(
                "alice",
                "reservation-42",
                List.of(new InvoiceLine("Rental", 1, Money.brl(new BigDecimal("100.00"))))));

        assertEquals("alice", invoice.customerId());
        assertEquals("reservation-42", invoice.reservationId());
        assertEquals(new BigDecimal("100.00"), invoice.total().amount());
        assertSame(invoice, repository.saved);
    }

    static class FakeRepository implements InvoiceRepository {
        Invoice saved;
        public Invoice save(Invoice invoice) { saved = invoice; return invoice; }
    }
}
