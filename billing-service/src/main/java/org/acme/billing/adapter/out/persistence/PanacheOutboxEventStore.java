package org.acme.billing.adapter.out.persistence;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithSession;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.event.InvoiceOpened;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.OutboxEventStore;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@ApplicationScoped
public class PanacheOutboxEventStore
        implements OutboxEventStore,
        PanacheRepository<OutboxEventEntity> {

    private final ObjectMapper objectMapper;

    public PanacheOutboxEventStore(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Uni<Void> appendInvoiceOpened(InvoiceOpened event) {
        OutboxEventEntity entity = new OutboxEventEntity();
        entity.eventId = event.eventId().toString();
        entity.eventType = "InvoiceOpened";
        entity.aggregateType = "Invoice";
        entity.aggregateId = event.invoiceId();
        entity.occurredAt = event.occurredAt();
        entity.publishedAt = null;
        entity.attempts = 0;

        try {
            entity.payload = objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            return Uni.createFrom().failure(
                    new IllegalStateException("Could not serialize InvoiceOpened event", e));
        }

        return persist(entity).replaceWithVoid();
    }

    @Override
    @WithSession
    public Uni<List<OutboxEvent>> findPending(int limit) {
        return find("publishedAt is null order by occurredAt")
                .page(0, limit)
                .list()
                .map(entities -> entities.stream()
                        .map(this::toModel)
                        .toList());
    }

    @Override
    @WithTransaction
    public Uni<Void> markPublished(OutboxEvent event, Instant publishedAt) {
        return update("publishedAt = ?1 where eventId = ?2",
                publishedAt,
                event.eventId().toString())
                .replaceWithVoid();
    }

    @Override
    @WithTransaction
    public Uni<Void> incrementAttempts(OutboxEvent event) {
        return update("attempts = attempts + 1 where eventId = ?1",
                event.eventId().toString())
                .replaceWithVoid();
    }

    private OutboxEvent toModel(OutboxEventEntity entity) {
        return new OutboxEvent(
                UUID.fromString(entity.eventId),
                entity.eventType,
                entity.aggregateType,
                entity.aggregateId,
                entity.payload,
                entity.occurredAt,
                entity.attempts);
    }
}
