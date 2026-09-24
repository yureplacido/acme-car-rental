package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;

import java.util.UUID;
import java.util.function.Supplier;

public interface InboundEventProcessor {

    Uni<Void> process(UUID eventId, Supplier<Uni<Void>> businessEffect);
}
