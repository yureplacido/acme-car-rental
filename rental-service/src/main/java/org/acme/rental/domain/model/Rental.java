package org.acme.rental.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public final class Rental {

    private final RentalId id;
    private final CustomerId customerId;
    private final ReservationId reservationId;
    private final LocalDate startDate;
    private LocalDate endDate;
    private RentalStatus status;

    private Rental(RentalId id,
                   CustomerId customerId,
                   ReservationId reservationId,
                   LocalDate startDate,
                   LocalDate endDate,
                   RentalStatus status) {
        this.id = id;
        this.customerId = Objects.requireNonNull(customerId);
        this.reservationId = Objects.requireNonNull(reservationId);
        this.startDate = Objects.requireNonNull(startDate);
        this.endDate = endDate;
        this.status = Objects.requireNonNull(status);
    }

    public static Rental start(CustomerId customerId,
                               ReservationId reservationId,
                               LocalDate startDate) {
        return new Rental(null, customerId, reservationId, startDate, null, RentalStatus.ACTIVE);
    }

    public static Rental rehydrate(RentalId id,
                                   CustomerId customerId,
                                   ReservationId reservationId,
                                   LocalDate startDate,
                                   LocalDate endDate,
                                   RentalStatus status) {
        return new Rental(id, customerId, reservationId, startDate, endDate,
                status == null ? RentalStatus.ACTIVE : status);
    }

    public Rental finish(LocalDate finishedAt) {
        Objects.requireNonNull(finishedAt, "finish date is required");
        if (status != RentalStatus.ACTIVE) {
            throw new IllegalStateException("only active rentals can be finished");
        }
        if (finishedAt.isBefore(startDate)) {
            throw new IllegalArgumentException("finish date cannot precede start date");
        }
        endDate = finishedAt;
        status = RentalStatus.COMPLETED;
        return this;
    }

    public Rental cancel() {
        if (status == RentalStatus.COMPLETED) {
            throw new IllegalStateException("completed rental cannot be cancelled");
        }
        status = RentalStatus.CANCELLED;
        return this;
    }

    public RentalId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public ReservationId reservationId() { return reservationId; }
    public LocalDate startDate() { return startDate; }
    public LocalDate endDate() { return endDate; }
    public RentalStatus status() { return status; }
}
