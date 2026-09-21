package org.acme.inventory.application.usecase;

import io.smallrye.mutiny.Uni;
import org.acme.inventory.application.port.out.ProcessedEventStore;
import org.acme.inventory.domain.event.VehicleRegistered;

import java.util.Objects;
import java.util.function.Function;

public class ConsumeVehicleRegistered {

    private final ProcessedEventStore processedEventStore;
    private final Function<VehicleRegistered, Uni<Void>> handler;

    public ConsumeVehicleRegistered(ProcessedEventStore processedEventStore,
                                    Function<VehicleRegistered, Uni<Void>> handler) {
        this.processedEventStore = Objects.requireNonNull(processedEventStore);
        this.handler = Objects.requireNonNull(handler);
    }

    public Uni<Void> handle(VehicleRegistered event) {
        return processedEventStore.isProcessed(event.eventId())
                .flatMap(isProcessed -> isProcessed
                        ? Uni.createFrom().voidItem()
                        : handler.apply(event)
                                .call(() -> processedEventStore.markProcessed(event.eventId())));
    }
}
