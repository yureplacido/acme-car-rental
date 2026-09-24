package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.RentalCompleted;

import java.util.Objects;
import java.util.function.Function;

public class ConsumeRentalCompleted {

    private final Function<RentalCompleted, Uni<Void>> handler;

    public ConsumeRentalCompleted(Function<RentalCompleted, Uni<Void>> handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    public Uni<Void> handle(RentalCompleted event) {
        return handler.apply(event);
    }
}