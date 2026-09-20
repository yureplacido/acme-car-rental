package org.acme.inventory.application.usecase;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.acme.inventory.domain.model.Vehicle;

@ApplicationScoped
public class BulkRegisterVehicles {

    private final RegisterVehicle registerVehicle;
    private final int maxConcurrency;

    @Inject
    public BulkRegisterVehicles(
            RegisterVehicle registerVehicle,
            @ConfigProperty(name = "inventory.bulk.max-concurrency", defaultValue = "4")
            int maxConcurrency) {
        if (maxConcurrency < 1) {
            throw new IllegalArgumentException("maxConcurrency must be positive");
        }
        this.registerVehicle = registerVehicle;
        this.maxConcurrency = maxConcurrency;
    }

    public Multi<Vehicle> handle(Multi<RegisterVehicle.Command> commands) {
        return commands
                .onItem().transformToUni(registerVehicle::handle)
                .merge(maxConcurrency);
    }
}
