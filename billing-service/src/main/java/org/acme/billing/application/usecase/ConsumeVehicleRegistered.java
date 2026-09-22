package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.VehicleRegistered;
import org.acme.billing.application.port.out.ProcessedEventStore;

import java.util.Objects;
import java.util.function.Function;

public class ConsumeVehicleRegistered {

    private final ProcessedEventStore processedEventStore;
    private final Function<VehicleRegistered, Uni<Void>> handler;

    public ConsumeVehicleRegistered(
            ProcessedEventStore processedEventStore,
            Function<VehicleRegistered, Uni<Void>> handler) {
        this.processedEventStore = Objects.requireNonNull(processedEventStore);
        this.handler = Objects.requireNonNull(handler);
    }

    public Uni<Void> handle(VehicleRegistered event) {
        return processedEventStore.tryClaim(event.eventId())
                .flatMap(claimed -> claimed
                        ? process(event)
                        : Uni.createFrom().voidItem());
    }

    private Uni<Void> process(VehicleRegistered event) {
        return handler.apply(event)
                .onFailure()
                .call(() -> processedEventStore.release(event.eventId()));
    }
}
