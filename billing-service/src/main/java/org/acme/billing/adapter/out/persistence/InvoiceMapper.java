package org.acme.billing.adapter.out.persistence;

import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceId;
import org.acme.billing.domain.model.InvoiceStatus;

public final class InvoiceMapper {

    private InvoiceMapper() {
    }

    public static Invoice toDomain(InvoiceEntity entity) {
        return Invoice.rehydrate(
                new InvoiceId(entity.id.toString()),
                entity.customerId,
                entity.reservationId,
                entity.lines,
                entity.status);
    }

    public static InvoiceEntity toEntity(Invoice invoice) {
        InvoiceEntity entity = new InvoiceEntity();
        entity.id = invoice.id() == null ? null : Long.valueOf(invoice.id().value());
        entity.customerId = invoice.customerId();
        entity.reservationId = invoice.reservationId();
        entity.status = invoice.status();
        entity.lines = invoice.lines();
        return entity;
    }
}