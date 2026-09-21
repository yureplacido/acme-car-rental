package org.acme.inventory.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.application.port.out.ProcessedEventStore;
import org.acme.inventory.domain.event.VehicleRegistered;

import java.util.Objects;
import java.util.function.Function;

@ApplicationScoped
public class ConsumeVehicleRegistered {

    private final ProcessedEventStore processedEventStore;
    private final Function<VehicleRegistered, Uni<Void>> handler;

    public ConsumeVehicleRegistered(ProcessedEventStore processedEventStore,
                                    Function<VehicleRegistered, Uni<Void>> handler) {
        this.processedEventStore = Objects.requireNonNull(processedEventStore);
        this.handler = Objects.requireNonNull(handler);
    }

    public Uni<Void> handle(VehicleRegistered event) {
        return processedEventStore.markIfNew(event.eventId())
                .flatMap(isNew -> isNew
                        ? handler.apply(event)
                        : Uni.createFrom().voidItem());
    }
}
