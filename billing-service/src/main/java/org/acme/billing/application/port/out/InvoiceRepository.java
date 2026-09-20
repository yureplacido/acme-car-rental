package org.acme.billing.application.port.out;

import org.acme.billing.domain.model.Invoice;

public interface InvoiceRepository {
    Invoice save(Invoice invoice);
}
