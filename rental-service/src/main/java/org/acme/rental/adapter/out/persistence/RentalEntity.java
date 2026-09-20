package org.acme.rental.adapter.out.persistence;

import io.quarkus.mongodb.panache.PanacheMongoEntity;

import java.time.LocalDate;

public class RentalEntity extends PanacheMongoEntity {
    // Keep the existing Mongo field name for compatibility; the domain calls it CustomerId.
    public String userId;
    public Long reservationId;
    public LocalDate startDate;
    public LocalDate endDate;
    public String status;
}
