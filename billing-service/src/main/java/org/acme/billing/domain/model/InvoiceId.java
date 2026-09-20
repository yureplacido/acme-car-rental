package org.acme.billing.domain.model;

public record InvoiceId(String value) {
    public InvoiceId {
        if (value != null && value.isBlank()) throw new IllegalArgumentException("invoice id cannot be blank");
    }
}
