package org.acme.inventory.adapter.out.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.acme.inventory.application.port.out.InventoryMetrics;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MicrometerInventoryMetricsTest {

    @Test
    void shouldIncrementVehicleRegisteredCounter() {
        MeterRegistry registry = new SimpleMeterRegistry();
        InventoryMetrics metrics = new MicrometerInventoryMetrics(registry);

        metrics.vehicleRegistered();
        metrics.vehicleRegistered();

        Counter counter = registry.find("inventory.vehicles.registered").counter();
        assertEquals(2, counter.count());
    }
}