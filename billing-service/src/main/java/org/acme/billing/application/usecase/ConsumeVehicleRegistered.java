package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.VehicleRegistered;

import java.util.Objects;
import java.util.function.Function;

public class ConsumeVehicleRegistered {

    private final Function<VehicleRegistered, Uni<Void>> handler;

    public ConsumeVehicleRegistered(Function<VehicleRegistered, Uni<Void>> handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    public Uni<Void> handle(VehicleRegistered event) {
        return handler.apply(event);
    }
}
