package org.acme.billing.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.ProcessedEventStore;

import java.util.UUID;

/**
 * Inbox durável (ADR 005): o claim é atômico via {@code INSERT ... ON CONFLICT DO NOTHING}.
 * Se o evento já foi processado, o insert não afeta linhas e {@code tryClaim} retorna false.
 */
@ApplicationScoped
public class PostgresProcessedEventStore implements ProcessedEventStore, PanacheRepository<ProcessedEventEntity> {

    @Override
    @WithTransaction
    public Uni<Boolean> tryClaim(UUID eventId) {
        return getSession()
                .flatMap(session -> session.createNativeQuery("""
                                INSERT INTO processed_event (event_id)
                                VALUES (:eventId)
                                ON CONFLICT (event_id) DO NOTHING
                                """)
                        .setParameter("eventId", eventId.toString())
                        .executeUpdate())
                .map(affected -> affected == 1);
    }

    @Override
    @WithTransaction
    public Uni<Void> release(UUID eventId) {
        return delete("eventId", eventId.toString()).replaceWithVoid();
    }
}