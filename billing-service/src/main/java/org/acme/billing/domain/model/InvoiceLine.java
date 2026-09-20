package org.acme.billing.domain.model;

import java.util.Objects;

public record InvoiceLine(String description, int quantity, Money unitPrice) {

    public InvoiceLine {
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(unitPrice, "unit price is required");
        if (description.isBlank()) throw new IllegalArgumentException("description is required");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
    }

    public Money total() {
        return new Money(unitPrice.amount().multiply(java.math.BigDecimal.valueOf(quantity)), unitPrice.currency());
    }
}
