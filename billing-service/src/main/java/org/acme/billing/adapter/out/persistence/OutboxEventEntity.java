package org.acme.billing.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(name = "outbox_event",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class OutboxEventEntity extends io.quarkus.hibernate.reactive.panache.PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public String eventId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    public String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    public int attempts;
}
