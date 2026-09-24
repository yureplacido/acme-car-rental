package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.event.RentalCompleted;
import org.acme.billing.application.usecase.ConsumeRentalCompleted;
import org.acme.billing.application.usecase.OpenInvoiceForRental;
import org.acme.billing.application.usecase.TransactionalInboxProcessor;
import org.acme.billing.domain.model.Money;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.util.function.Function;

@ApplicationScoped
public class KafkaRentalCompletedConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaRentalCompletedConsumer.class);

    private final ObjectMapper objectMapper;
    private final ConsumeRentalCompleted consumer;
    private final TransactionalInboxProcessor inboxProcessor;

    @Inject
    public KafkaRentalCompletedConsumer(
            ObjectMapper objectMapper,
            OpenInvoiceForRental openInvoiceForRental,
            TransactionalInboxProcessor inboxProcessor) {
        this(
                objectMapper,
                event -> openInvoiceForRental.handle(toCommand(event)).replaceWithVoid(),
                inboxProcessor);
    }

    KafkaRentalCompletedConsumer(
            ObjectMapper objectMapper,
            Function<RentalCompleted, Uni<Void>> handler,
            TransactionalInboxProcessor inboxProcessor) {
        this.objectMapper = objectMapper;
        this.consumer = new ConsumeRentalCompleted(handler);
        this.inboxProcessor = inboxProcessor;
    }

    @Incoming("rental-completed-in")
    public Uni<Void> consume(String payload) {
        return Uni.createFrom()
                .item(() -> deserialize(payload))
                .flatMap(event -> inboxProcessor.process(
                        event.eventId(),
                        () -> consumer.handle(event)))
                .onFailure().invoke(f ->
                        LOG.errorf(
                                "Error processing RentalCompleted event: %s",
                                f.toString(),
                                f));
    }

    private RentalCompleted deserialize(String payload) {
        RentalCompleted event;
        try {
            event = objectMapper.readValue(payload, RentalCompleted.class);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException(
                    "Could not deserialize RentalCompleted event", e);
        }
        if (event.eventId() == null || event.reservationId() == null
                || event.dailyRate() == null || event.startDate() == null || event.endDate() == null) {
            throw new IllegalArgumentException(
                    "RentalCompleted event is missing required fields");
        }
        return event;
    }

    static OpenInvoiceForRental.Command toCommand(RentalCompleted event) {
        Money dailyRate = new Money(event.dailyRate().amount(), event.dailyRate().currency());
        return new OpenInvoiceForRental.Command(
                new OpenInvoiceForRental.RentalDetails(
                        event.reservationId(),
                        event.startDate(),
                        event.endDate(),
                        dailyRate,
                        event.licensePlate()));
    }
}
