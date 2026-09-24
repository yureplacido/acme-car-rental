package org.acme.billing.adapter.out.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.util.UUID;

@Entity
@Table(name = "processed_event")
public class ProcessedEventEntity {

    @Id
    @Column(name = "event_id")
    public String eventId;

    public static ProcessedEventEntity of(UUID eventId) {
        ProcessedEventEntity entity = new ProcessedEventEntity();
        entity.eventId = eventId.toString();
        return entity;
    }
}