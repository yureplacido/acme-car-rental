package org.acme.billing.adapter.out.persistence;

import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.domain.model.Invoice;

import java.util.UUID;

@ApplicationScoped
public class NoopInvoiceRepository implements InvoiceRepository {
    @Override
    public Invoice save(Invoice invoice) {
        // Placeholder adapter until the Mongo persistence chapter is implemented.
        return invoice;
    }
}
