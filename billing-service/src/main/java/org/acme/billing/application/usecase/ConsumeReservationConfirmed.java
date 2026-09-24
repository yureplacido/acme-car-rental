package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.ReservationConfirmed;

import java.util.Objects;
import java.util.function.Function;

public class ConsumeReservationConfirmed {

    private final Function<ReservationConfirmed, Uni<Void>> handler;

    public ConsumeReservationConfirmed(Function<ReservationConfirmed, Uni<Void>> handler) {
        this.handler = Objects.requireNonNull(handler);
    }

    public Uni<Void> handle(ReservationConfirmed event) {
        return handler.apply(event);
    }
}