package org.acme.inventory.domain.model;

import java.util.Objects;

public final class Vehicle {

    private final VehicleId id;
    private final LicensePlate licensePlate;
    private final VehicleSpecifications specifications;
    private VehicleLocation location;
    private VehicleStatus status;
    private final VehicleDailyRate dailyRate;
    private OdometerReading odometer;
    private VehicleCondition condition;

    private Vehicle(VehicleId id,
                    LicensePlate licensePlate,
                    VehicleSpecifications specifications,
                    VehicleLocation location,
                    VehicleStatus status,
                    VehicleDailyRate dailyRate,
                    OdometerReading odometer,
                    VehicleCondition condition) {
        this.id = id;
        this.licensePlate = Objects.requireNonNull(licensePlate);
        this.specifications = Objects.requireNonNull(specifications);
        this.location = location;
        this.status = Objects.requireNonNull(status);
        this.dailyRate = dailyRate;
        this.odometer = Objects.requireNonNull(odometer);
        this.condition = Objects.requireNonNull(condition);
    }

    public static Vehicle register(LicensePlate licensePlate,
                                   VehicleSpecifications specifications,
                                   VehicleLocation location,
                                   VehicleDailyRate dailyRate) {
        return new Vehicle(
                null,
                licensePlate,
                specifications,
                location,
                VehicleStatus.AVAILABLE,
                dailyRate,
                new OdometerReading(0L),
                VehicleCondition.GOOD);
    }

    public static Vehicle rehydrate(VehicleId id,
                                    LicensePlate licensePlate,
                                    VehicleSpecifications specifications,
                                    VehicleLocation location,
                                    VehicleStatus status,
                                    VehicleDailyRate dailyRate,
                                    OdometerReading odometer,
                                    VehicleCondition condition) {
        return new Vehicle(
                id,
                licensePlate,
                specifications,
                location,
                status == null ? VehicleStatus.AVAILABLE : status,
                dailyRate,
                odometer == null ? new OdometerReading(0L) : odometer,
                condition == null ? VehicleCondition.GOOD : condition);
    }

    public Vehicle decommission() {
        if (status == VehicleStatus.DECOMMISSIONED) {
            throw new IllegalStateException("vehicle is already decommissioned");
        }
        status = VehicleStatus.DECOMMISSIONED;
        return this;
    }

    public Vehicle sendToMaintenance() {
        if (status == VehicleStatus.DECOMMISSIONED) {
            throw new IllegalStateException("decommissioned vehicle cannot enter maintenance");
        }
        status = VehicleStatus.IN_MAINTENANCE;
        return this;
    }

    public Vehicle releaseFromMaintenance() {
        if (status != VehicleStatus.IN_MAINTENANCE) {
            throw new IllegalStateException("vehicle is not in maintenance");
        }
        status = VehicleStatus.AVAILABLE;
        return this;
    }

    public Vehicle relocate(VehicleLocation newLocation) {
        this.location = Objects.requireNonNull(newLocation);
        return this;
    }

    public Vehicle recordOdometer(OdometerReading newReading) {
        Objects.requireNonNull(newReading, "odometer reading is required");
        if (newReading.kilometers() < odometer.kilometers()) {
            throw new IllegalArgumentException("odometer cannot go backwards");
        }
        odometer = newReading;
        return this;
    }

    public Vehicle changeCondition(VehicleCondition newCondition) {
        condition = Objects.requireNonNull(newCondition, "vehicle condition is required");
        return this;
    }

    public boolean canBeOffered() {
        return status == VehicleStatus.AVAILABLE;
    }

    public VehicleDailyRate dailyRate() { return dailyRate; }
    public VehicleId id() { return id; }
    public LicensePlate licensePlate() { return licensePlate; }
    public VehicleSpecifications specifications() { return specifications; }
    public VehicleLocation location() { return location; }
    public VehicleStatus status() { return status; }
    public OdometerReading odometer() { return odometer; }
    public VehicleCondition condition() { return condition; }
}
