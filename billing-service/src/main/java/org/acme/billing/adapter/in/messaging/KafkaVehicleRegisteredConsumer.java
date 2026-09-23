package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
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

    private final ObjectMapper objectMapper;
    private final ConsumeVehicleRegistered consumer;

    @Inject
    public KafkaVehicleRegisteredConsumer(ObjectMapper objectMapper) {
        this(objectMapper, KafkaVehicleRegisteredConsumer::process);
    }

    KafkaVehicleRegisteredConsumer(
            ObjectMapper objectMapper,
            Function<VehicleRegistered, Uni<Void>> handler) {
        this.objectMapper = objectMapper;
        this.consumer = new ConsumeVehicleRegistered(handler);
    }

    @Incoming("vehicle-registered-in")
    public Uni<Void> consume(String payload) {
        return Uni.createFrom()
                .item(() -> deserialize(payload))
                .flatMap(consumer::handle);
    }

    private VehicleRegistered deserialize(String payload) {
        try {
            return objectMapper.readValue(payload, VehicleRegistered.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Could not deserialize VehicleRegistered event", e);
        }
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
