package org.acme.billing.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.domain.model.Invoice;

import java.util.Optional;

@ApplicationScoped
public class PanacheInvoiceRepository implements InvoiceRepository, PanacheRepository<InvoiceEntity> {

    @Override
    @WithTransaction
    public Uni<Invoice> save(Invoice invoice) {
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