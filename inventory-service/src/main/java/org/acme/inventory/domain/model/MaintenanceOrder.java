package org.acme.inventory.domain.model;

import java.time.LocalDate;
import java.util.Objects;

public final class MaintenanceOrder {

    private final MaintenanceOrderId id;
    private final VehicleId vehicleId;
    private final MaintenanceType type;
    private final LocalDate openedAt;
    private final String description;
    private MaintenanceStatus status;

    private MaintenanceOrder(MaintenanceOrderId id,
                              VehicleId vehicleId,
                              MaintenanceType type,
                              LocalDate openedAt,
                              String description,
                              MaintenanceStatus status) {
        this.id = id;
        this.vehicleId = Objects.requireNonNull(vehicleId);
        this.type = Objects.requireNonNull(type);
        this.openedAt = Objects.requireNonNull(openedAt);
        this.description = requireText(description, "description");
        this.status = Objects.requireNonNull(status);
    }

    public static MaintenanceOrder open(VehicleId vehicleId,
                                        MaintenanceType type,
                                        LocalDate openedAt,
                                        String description) {
        return new MaintenanceOrder(null, vehicleId, type, openedAt, description, MaintenanceStatus.OPEN);
    }

    public static MaintenanceOrder rehydrate(MaintenanceOrderId id,
                                             VehicleId vehicleId,
                                             MaintenanceType type,
                                             LocalDate openedAt,
                                             String description,
                                             MaintenanceStatus status) {
        return new MaintenanceOrder(id, vehicleId, type, openedAt, description,
                status == null ? MaintenanceStatus.OPEN : status);
    }

    public MaintenanceOrder start() {
        if (status != MaintenanceStatus.OPEN) {
            throw new IllegalStateException("only open maintenance orders can be started");
        }
        status = MaintenanceStatus.IN_PROGRESS;
        return this;
    }

    public MaintenanceOrder complete() {
        if (status != MaintenanceStatus.IN_PROGRESS) {
            throw new IllegalStateException("only in-progress maintenance orders can be completed");
        }
        status = MaintenanceStatus.COMPLETED;
        return this;
    }

    public MaintenanceOrder cancel() {
        if (status == MaintenanceStatus.COMPLETED) {
            throw new IllegalStateException("completed maintenance order cannot be cancelled");
        }
        if (status == MaintenanceStatus.CANCELLED) {
            throw new IllegalStateException("maintenance order is already cancelled");
        }
        status = MaintenanceStatus.CANCELLED;
        return this;
    }

    private static String requireText(String value, String field) {
        Objects.requireNonNull(value, field + " is required");
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        return value.trim();
    }

    public MaintenanceOrderId id() { return id; }
    public VehicleId vehicleId() { return vehicleId; }
    public MaintenanceType type() { return type; }
    public LocalDate openedAt() { return openedAt; }
    public String description() { return description; }
    public MaintenanceStatus status() { return status; }
}
