package org.acme.rental.adapter.out.persistence;

import io.quarkus.mongodb.panache.PanacheMongoEntity;

import java.time.LocalDate;

public class RentalEntity extends PanacheMongoEntity {
    public String customerId;
    public Long reservationId;
    public LocalDate startDate;
    public LocalDate endDate;
    public String status;
}
