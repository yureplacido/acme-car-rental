package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.event.VehicleRegistered;
import org.acme.billing.application.usecase.ConsumeVehicleRegistered;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.util.function.Function;

@ApplicationScoped
public class KafkaVehicleRegisteredConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaVehicleRegisteredConsumer.class);

    private final EventJsonCodec codec;
    private final ConsumeVehicleRegistered consumer;

    @Inject
    public KafkaVehicleRegisteredConsumer(EventJsonCodec codec) {
        this(codec, KafkaVehicleRegisteredConsumer::process);
    }

    KafkaVehicleRegisteredConsumer(
            EventJsonCodec codec,
            Function<VehicleRegistered, Uni<Void>> handler) {
        this.codec = codec;
        this.consumer = new ConsumeVehicleRegistered(handler);
    }

    @Incoming("vehicle-registered-in")
    public Uni<Void> consume(String payload) {
        return Uni.createFrom()
                .item(() -> deserialize(payload))
                .flatMap(consumer::handle);
    }

    private VehicleRegistered deserialize(String payload) {
        VehicleRegistered event = codec.decode(payload, VehicleRegistered.class);

        if (event.eventId() == null || event.vehicleId() == null) {
            throw new IllegalArgumentException(
                    "VehicleRegistered event is missing required fields");
        }

        return event;
    }

    private static Uni<Void> process(VehicleRegistered event) {
        LOG.infof(
                "Billing received VehicleRegistered event: eventId=%s vehicleId=%s licensePlate=%s",
                event.eventId(),
                event.vehicleId().value(),
                event.licensePlate());

        return Uni.createFrom().voidItem();
    }
}
