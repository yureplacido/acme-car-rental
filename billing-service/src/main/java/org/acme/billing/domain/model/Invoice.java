package org.acme.billing.domain.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Invoice {

    private final InvoiceId id;
    private final String customerId;
    private final String reservationId;
    private final List<InvoiceLine> lines;
    private InvoiceStatus status;

    private Invoice(InvoiceId id,
                    String customerId,
                    String reservationId,
                    List<InvoiceLine> lines,
                    InvoiceStatus status) {
        this.id = id;
        this.customerId = Objects.requireNonNull(customerId);
        this.reservationId = Objects.requireNonNull(reservationId);
        this.lines = new ArrayList<>(Objects.requireNonNull(lines));
        this.status = Objects.requireNonNull(status);
    }

    public static Invoice draft(String customerId, String reservationId, List<InvoiceLine> lines) {
        if (lines == null || lines.isEmpty()) {
            throw new IllegalArgumentException("invoice must contain at least one line");
        }
        return new Invoice(null, customerId, reservationId, lines, InvoiceStatus.DRAFT);
    }

    public Invoice open() {
        if (lines.isEmpty()) throw new IllegalStateException("invoice must contain lines");
        if (status != InvoiceStatus.DRAFT) throw new IllegalStateException("only draft invoices can be opened");
        status = InvoiceStatus.OPEN;
        return this;
    }

    public Invoice markPaid() {
        if (status != InvoiceStatus.OPEN) throw new IllegalStateException("only open invoices can be paid");
        status = InvoiceStatus.PAID;
        return this;
    }

    public Invoice cancel() {
        if (status == InvoiceStatus.PAID) throw new IllegalStateException("paid invoice cannot be cancelled");
        status = InvoiceStatus.CANCELLED;
        return this;
    }

    public Money total() {
        return lines.stream()
                .map(InvoiceLine::total)
                .reduce((left, right) -> left.add(right))
                .orElseThrow();
    }

    public InvoiceId id() { return id; }
    public String customerId() { return customerId; }
    public String reservationId() { return reservationId; }
    public List<InvoiceLine> lines() { return Collections.unmodifiableList(lines); }
    public InvoiceStatus status() { return status; }
}
