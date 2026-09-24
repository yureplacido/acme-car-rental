package org.acme.billing.adapter.out.persistence;

import io.quarkus.hibernate.reactive.panache.PanacheRepository;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.ProcessedEventStore;

import java.util.UUID;

/**
 * Durable inbox store (ADR 005).
 *
 * The claim intentionally does not start its own transaction. The caller's
 * @WithTransaction boundary owns both the claim and the business effect.
 */
@ApplicationScoped
public class PostgresProcessedEventStore
        implements ProcessedEventStore, PanacheRepository<ProcessedEventEntity> {

    @Override
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
}
