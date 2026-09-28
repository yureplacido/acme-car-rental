package org.acme.inventory.adapter.out.messaging;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.Record;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.application.port.out.EventPublisher;
import org.acme.inventory.domain.event.VehicleRegistered;
import org.eclipse.microprofile.reactive.messaging.Channel;
import org.eclipse.microprofile.reactive.messaging.Message;

@ApplicationScoped
public class VehicleRegisteredEventPublisher implements EventPublisher<VehicleRegistered> {

    private final EventJsonCodec codec;
    private final MutinyEmitter<Record<String, String>> emitter;

    public VehicleRegisteredEventPublisher(
            EventJsonCodec codec,
            @Channel("vehicle-registered-out") MutinyEmitter<Record<String, String>> emitter) {
        this.codec = codec;
        this.emitter = emitter;
    }

    @Override
    public Uni<Void> publish(VehicleRegistered event) {
        return Uni.createFrom()
                .item(() -> Record.of(
                        event.vehicleId().value().toString(),
                        codec.encode(event)))
                .flatMap(record -> emitter.sendMessage(Message.of(record)));
    }
}
