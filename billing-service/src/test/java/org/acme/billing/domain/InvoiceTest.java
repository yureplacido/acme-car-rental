package org.acme.billing.domain;

import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.InvoiceStatus;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceTest {

    @Test
    void shouldCalculateInvoiceTotal() {
        Invoice invoice = Invoice.draft(
                "alice",
                "reservation-42",
                List.of(
                        new InvoiceLine("Rental", 2, Money.brl(new BigDecimal("100.00"))),
                        new InvoiceLine("Insurance", 1, Money.brl(new BigDecimal("50.00")))));

        assertEquals(new BigDecimal("250.00"), invoice.total().amount());
        assertEquals("BRL", invoice.total().currency());
        assertEquals(InvoiceStatus.DRAFT, invoice.status());
    }

    @Test
    void shouldOpenDraftInvoice() {
        Invoice invoice = Invoice.draft(
                "alice",
                "reservation-42",
                List.of(new InvoiceLine("Rental", 1, Money.brl(new BigDecimal("100.00")))));

        invoice.open();

        assertEquals(InvoiceStatus.OPEN, invoice.status());
    }

    @Test
    void shouldNotPayDraftInvoice() {
        Invoice invoice = Invoice.draft(
                "alice",
                "reservation-42",
                List.of(new InvoiceLine("Rental", 1, Money.brl(new BigDecimal("100.00")))));

        assertThrows(IllegalStateException.class, invoice::markPaid);
    }
}
