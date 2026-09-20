package org.acme.inventory.domain.model;

import java.util.Objects;

public final class Vehicle {

    private final VehicleId id;
    private final LicensePlate licensePlate;
    private final VehicleSpecifications specifications;
    private VehicleLocation location;
    private VehicleStatus status;
    private VehicleDailyRate dailyRate;

    private Vehicle(VehicleId id,
                    LicensePlate licensePlate,
                    VehicleSpecifications specifications,
                    VehicleLocation location,
                    VehicleStatus status,
                    VehicleDailyRate dailyRate) {
        this.id = id;
        this.licensePlate = Objects.requireNonNull(licensePlate);
        this.specifications = Objects.requireNonNull(specifications);
        this.location = location;
        this.status = Objects.requireNonNull(status);
        this.dailyRate = dailyRate;
    }

    public static Vehicle register(LicensePlate licensePlate,
                                   VehicleSpecifications specifications,
                                   VehicleLocation location,
                                   VehicleDailyRate dailyRate) {
        return new Vehicle(null, licensePlate, specifications, location, VehicleStatus.AVAILABLE, dailyRate);
    }

    public static Vehicle rehydrate(VehicleId id,
                                    LicensePlate licensePlate,
                                    VehicleSpecifications specifications,
                                    VehicleLocation location,
                                    VehicleStatus status,
                                    VehicleDailyRate dailyRate) {
        return new Vehicle(id, licensePlate, specifications, location,
                status == null ? VehicleStatus.AVAILABLE : status,
                dailyRate);
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

    public boolean canBeOffered() {
        return status == VehicleStatus.AVAILABLE;
    }

    public VehicleDailyRate dailyRate() { return dailyRate; }
    public VehicleId id() { return id; }
    public LicensePlate licensePlate() { return licensePlate; }
    public VehicleSpecifications specifications() { return specifications; }
    public VehicleLocation location() { return location; }
    public VehicleStatus status() { return status; }
}
