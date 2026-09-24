package org.acme.billing.domain;

import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class InvoiceLineTest {

    private static final LocalDate START = LocalDate.of(2026, 10, 1);

    @Test
    void shouldComputeOneUnitPerRentalDay() {
        InvoiceLine line = InvoiceLine.rentalDays(
                "Aluguel de veículo",
                START,
                LocalDate.of(2026, 10, 5),
                Money.brl(new BigDecimal("150.00")));

        assertEquals(5, line.quantity());
        assertEquals("Aluguel de veículo", line.description());
        assertEquals(new BigDecimal("150.00"), line.unitPrice().amount());
        assertEquals(new BigDecimal("750.00"), line.total().amount());
    }

    @Test
    void shouldChargeAtLeastOneDayForSameDayRental() {
        InvoiceLine line = InvoiceLine.rentalDays(
                "Aluguel de veículo",
                START,
                START,
                Money.brl(new BigDecimal("150.00")));

        assertEquals(1, line.quantity());
    }

    @Test
    void shouldCountBothEndpointsInclusive() {
        InvoiceLine line = InvoiceLine.rentalDays(
                "Aluguel de veículo",
                START,
                START.plusDays(1),
                Money.brl(new BigDecimal("100.00")));

        assertEquals(2, line.quantity());
        assertEquals(new BigDecimal("200.00"), line.total().amount());
    }

    @Test
    void shouldRejectEndBeforeStart() {
        assertThrows(IllegalArgumentException.class, () -> InvoiceLine.rentalDays(
                "Aluguel de veículo",
                START,
                START.minusDays(1),
                Money.brl(new BigDecimal("150.00"))));
    }
}