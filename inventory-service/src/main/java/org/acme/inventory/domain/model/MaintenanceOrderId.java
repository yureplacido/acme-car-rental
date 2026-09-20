package org.acme.inventory.domain.model;

public record MaintenanceOrderId(Long value) {
    public MaintenanceOrderId {
        if (value != null && value <= 0) {
            throw new IllegalArgumentException("maintenance order id must be positive");
        }
    }
}
