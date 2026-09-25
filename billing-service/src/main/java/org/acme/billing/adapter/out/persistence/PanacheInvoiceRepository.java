package org.acme.billing.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.event.InvoiceOpened;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.acme.billing.domain.model.Invoice;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@ApplicationScoped
public class PanacheInvoiceRepository implements InvoiceRepository, PanacheRepository<InvoiceEntity> {

    private final OutboxEventStore outboxEventStore;

    @Inject
    public PanacheInvoiceRepository(OutboxEventStore outboxEventStore) {
        this.outboxEventStore = outboxEventStore;
    }

    @Override
    @WithTransaction
    public Uni<Invoice> save(Invoice invoice) {
        return persistInvoice(invoice);
    }

    @Override
    @WithTransaction
    public Uni<Invoice> saveOpenedWithOutbox(Invoice invoice) {
        return persistInvoice(invoice)
                .flatMap(saved -> {
                    InvoiceOpened event = new InvoiceOpened(
                            UUID.randomUUID(),
                            1,
                            Instant.now(),
                            saved.id().value(),
                            saved.customerId(),
                            saved.reservationId(),
                            saved.total().amount(),
                            saved.total().currency());
                    return outboxEventStore.appendInvoiceOpened(event)
                            .replaceWith(saved);
                });
    }

    private Uni<Invoice> persistInvoice(Invoice invoice) {
        InvoiceEntity entity = InvoiceMapper.toEntity(invoice);
        if (invoice.id() == null) {
            return persist(entity).map(InvoiceMapper::toDomain);
        }
        return getSession()
                .flatMap(session -> session.merge(entity))
                .map(InvoiceMapper::toDomain);
    }

    @Override
    @WithSession
    public Uni<Optional<Invoice>> findByReservationId(String reservationId) {
        return find("reservationId", reservationId)
                .firstResult()
                .map(entity -> entity == null ? Optional.empty() : Optional.of(InvoiceMapper.toDomain(entity)));
    }
}
