package org.acme.inventory.adapter.out.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.OutgoingKafkaRecordMetadata;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.application.port.out.EventPublisher;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class KafkaEventPublisher implements EventPublisher {

    private final ObjectMapper objectMapper;
    private final MutinyEmitter<String> emitter;

    public KafkaEventPublisher(
            ObjectMapper objectMapper,
            @Channel("vehicle-registered-out") MutinyEmitter<String> emitter) {
        this.objectMapper = objectMapper;
        this.emitter = emitter;
    }

    @Override
    public Uni<Void> publish(VehicleRegistered event) {
        return Uni.createFrom()
                .item(() -> serialize(event))
                .flatMap(payload -> emitter.send(
                        Message.of(payload)
                                .addMetadata(OutgoingKafkaRecordMetadata.<String>builder()
                                        .withKey(event.vehicleId().value().toString())
                                        .build())));
    }

    private String serialize(VehicleRegistered event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not serialize VehicleRegistered event", e);
        }
    }
}
