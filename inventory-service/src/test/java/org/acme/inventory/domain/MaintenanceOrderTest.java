package org.acme.inventory.domain;

import org.acme.inventory.domain.model.MaintenanceOrder;
import org.acme.inventory.domain.model.MaintenanceOrderId;
import org.acme.inventory.domain.model.MaintenanceStatus;
import org.acme.inventory.domain.model.MaintenanceType;
import org.acme.inventory.domain.model.VehicleId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.*;

class MaintenanceOrderTest {

    @Test
    void shouldOpenMaintenanceOrder() {
        MaintenanceOrder order = MaintenanceOrder.open(
                new VehicleId(10L),
                MaintenanceType.PREVENTIVE,
                LocalDate.of(2035, 3, 20),
                "scheduled inspection");

        assertEquals(MaintenanceStatus.OPEN, order.status());
        assertEquals(new VehicleId(10L), order.vehicleId());
        assertNull(order.id());
    }

    @Test
    void shouldStartOpenMaintenanceOrder() {
        MaintenanceOrder order = order();

        order.start();

        assertEquals(MaintenanceStatus.IN_PROGRESS, order.status());
    }

    @Test
    void shouldCompleteInProgressMaintenanceOrder() {
        MaintenanceOrder order = order();
        order.start();

        order.complete();

        assertEquals(MaintenanceStatus.COMPLETED, order.status());
    }

    @Test
    void shouldNotCompleteOpenMaintenanceOrder() {
        MaintenanceOrder order = order();

        assertThrows(IllegalStateException.class, order::complete);
    }

    @Test
    void shouldNotCancelCompletedMaintenanceOrder() {
        MaintenanceOrder order = order();
        order.start();
        order.complete();

        assertThrows(IllegalStateException.class, order::cancel);
    }

    @Test
    void shouldCreatePersistentIdentity() {
        MaintenanceOrder order = MaintenanceOrder.rehydrate(
                new MaintenanceOrderId(42L),
                new VehicleId(10L),
                MaintenanceType.CORRECTIVE,
                LocalDate.of(2035, 3, 20),
                "brake repair",
                MaintenanceStatus.OPEN);

        assertEquals(new MaintenanceOrderId(42L), order.id());
    }

    private MaintenanceOrder order() {
        return MaintenanceOrder.open(
                new VehicleId(10L),
                MaintenanceType.PREVENTIVE,
                LocalDate.of(2035, 3, 20),
                "scheduled inspection");
    }
}
