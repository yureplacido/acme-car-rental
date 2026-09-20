package org.acme.reservation.domain.model;

import java.util.Objects;

public final class Reservation {

    private final ReservationId id;
    private final CustomerId customerId;
    private final VehicleId vehicleId;
    private final RentalPeriod period;
    private ReservationStatus status;

    private Reservation(ReservationId id,
                        CustomerId customerId,
                        VehicleId vehicleId,
                        RentalPeriod period,
                        ReservationStatus status) {
        this.id = id;
        this.customerId = Objects.requireNonNull(customerId);
        this.vehicleId = Objects.requireNonNull(vehicleId);
        this.period = Objects.requireNonNull(period);
        this.status = Objects.requireNonNull(status);
    }

    public static Reservation create(CustomerId customerId,
                                     VehicleId vehicleId,
                                     RentalPeriod period) {
        return new Reservation(null, customerId, vehicleId, period, ReservationStatus.PENDING);
    }

    public static Reservation rehydrate(ReservationId id,
                                        CustomerId customerId,
                                        VehicleId vehicleId,
                                        RentalPeriod period,
                                        ReservationStatus status) {
        return new Reservation(id, customerId, vehicleId, period,
                status == null ? ReservationStatus.PENDING : status);
    }

    public Reservation confirm() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("only pending reservations can be confirmed");
        }
        status = ReservationStatus.CONFIRMED;
        return this;
    }

    public Reservation cancel() {
        if (status == ReservationStatus.COMPLETED || status == ReservationStatus.CANCELLED) {
            throw new IllegalStateException("reservation cannot be cancelled in its current state");
        }
        status = ReservationStatus.CANCELLED;
        return this;
    }

    public Reservation reject() {
        if (status != ReservationStatus.PENDING) {
            throw new IllegalStateException("only pending reservations can be rejected");
        }
        status = ReservationStatus.REJECTED;
        return this;
    }

    public Reservation complete() {
        if (status != ReservationStatus.CONFIRMED) {
            throw new IllegalStateException("only confirmed reservations can be completed");
        }
        status = ReservationStatus.COMPLETED;
        return this;
    }

    public boolean active() {
        return status == ReservationStatus.PENDING || status == ReservationStatus.CONFIRMED;
    }

    public boolean conflictsWith(Reservation other) {
        return active()
                && other.active()
                && vehicleId.equals(other.vehicleId)
                && period.overlaps(other.period);
    }

    public ReservationId id() { return id; }
    public CustomerId customerId() { return customerId; }
    public VehicleId vehicleId() { return vehicleId; }
    public RentalPeriod period() { return period; }
    public ReservationStatus status() { return status; }
}
