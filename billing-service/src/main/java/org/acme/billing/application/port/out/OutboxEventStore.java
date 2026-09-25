package org.acme.billing.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.InvoiceOpened;

public interface OutboxEventStore {

    Uni<Void> appendInvoiceOpened(InvoiceOpened event);
}
