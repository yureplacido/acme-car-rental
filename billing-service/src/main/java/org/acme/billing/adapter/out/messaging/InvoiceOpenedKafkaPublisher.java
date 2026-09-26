package org.acme.billing.adapter.out.messaging;

import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.MutinyEmitter;
import io.smallrye.reactive.messaging.kafka.KafkaRecord;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.model.OutboxEvent;
import org.acme.billing.application.port.out.EventPublisher;
import org.eclipse.microprofile.reactive.messaging.Channel;

@ApplicationScoped
public class InvoiceOpenedKafkaPublisher implements EventPublisher {

    private final MutinyEmitter<String> emitter;

    public InvoiceOpenedKafkaPublisher(
            @Channel("invoice-opened-out") MutinyEmitter<String> emitter) {
        this.emitter = emitter;
    }

    @Override
    public Uni<Void> publish(OutboxEvent event) {
        return emitter.sendMessage(
                KafkaRecord.<String, String>of(
                        event.aggregateId(),
                        event.payload()));
    }
}
