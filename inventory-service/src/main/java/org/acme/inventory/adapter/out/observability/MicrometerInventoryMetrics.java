package org.acme.inventory.adapter.out.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.application.port.out.InventoryMetrics;

@ApplicationScoped
public class MicrometerInventoryMetrics implements InventoryMetrics {

    private final Counter vehiclesRegistered;

    public MicrometerInventoryMetrics(MeterRegistry registry) {
        this.vehiclesRegistered = registry.counter("inventory.vehicles.registered");
    }

    @Override
    public void vehicleRegistered() {
        vehiclesRegistered.increment();
    }
}
