package org.acme.billing.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.InvoiceOpened;
import org.acme.billing.application.model.OutboxEvent;

import java.time.Instant;
import java.util.List;

public interface OutboxEventStore {

    Uni<Void> appendInvoiceOpened(InvoiceOpened event);

    Uni<List<OutboxEvent>> findPending(int limit);

    Uni<Void> markPublished(OutboxEvent event, Instant publishedAt);

    Uni<Void> incrementAttempts(OutboxEvent event);
}
