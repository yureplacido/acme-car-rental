package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceLine;

import java.util.List;

@ApplicationScoped
public class CreateInvoice {

    private final InvoiceRepository repository;

    @Inject
    public CreateInvoice(InvoiceRepository repository) {
        this.repository = repository;
    }

    public Uni<Invoice> handle(Command command) {
        return repository.save(Invoice.draft(
                command.customerId(),
                command.reservationId(),
                command.lines()));
    }

    public record Command(String customerId, String reservationId, List<InvoiceLine> lines) {
    }
}
