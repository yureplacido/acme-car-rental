package org.acme.billing.application.port.out;

import io.smallrye.mutiny.Uni;
import org.acme.billing.domain.model.Invoice;

import java.util.Optional;

public interface InvoiceRepository {
    Uni<Invoice> save(Invoice invoice);
    Uni<Optional<Invoice>> findByReservationId(String reservationId);
}
