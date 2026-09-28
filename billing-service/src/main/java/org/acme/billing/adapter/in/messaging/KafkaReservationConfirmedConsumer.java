package org.acme.billing.adapter.in.messaging;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.event.ReservationConfirmed;
import org.acme.billing.application.usecase.ConsumeReservationConfirmed;
import org.acme.billing.application.usecase.CreateInvoice;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.eclipse.microprofile.reactive.messaging.Incoming;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.function.Function;

@ApplicationScoped
public class KafkaReservationConfirmedConsumer {

    private static final Logger LOG = Logger.getLogger(KafkaReservationConfirmedConsumer.class);

    private final EventJsonCodec codec;
    private final ConsumeReservationConfirmed consumer;
    private final InboundEventProcessor inboxProcessor;

    @Inject
    public KafkaReservationConfirmedConsumer(
            EventJsonCodec codec,
            CreateInvoice createInvoice,
            InboundEventProcessor inboxProcessor) {
        this(
                codec,
                event -> createInvoice.handle(toCommand(event)).replaceWithVoid(),
                inboxProcessor);
    }

    KafkaReservationConfirmedConsumer(
            EventJsonCodec codec,
            Function<ReservationConfirmed, Uni<Void>> handler) {
        this(codec, handler, (eventId, effect) -> effect.get());
    }

    KafkaReservationConfirmedConsumer(
            EventJsonCodec codec,
            Function<ReservationConfirmed, Uni<Void>> handler,
            InboundEventProcessor inboxProcessor) {
        this.codec = codec;
        this.consumer = new ConsumeReservationConfirmed(handler);
        this.inboxProcessor = inboxProcessor;
    }

    @Incoming("reservation-confirmed-in")
    public Uni<Void> consume(String payload) {
        return Uni.createFrom()
                .item(() -> deserialize(payload))
                .flatMap(event -> inboxProcessor.process(
                        event.eventId(),
                        () -> consumer.handle(event)))
                .onFailure().invoke(f ->
                        LOG.errorf(
                                "Error processing ReservationConfirmed event: %s",
                                f.toString(),
                                f));
    }

    private ReservationConfirmed deserialize(String payload) {
        ReservationConfirmed event = codec.decode(payload, ReservationConfirmed.class);

        if (event.eventId() == null || event.reservationId() == null
                || event.dailyRate() == null || event.from() == null || event.to() == null) {
            throw new IllegalArgumentException(
                    "ReservationConfirmed event is missing required fields");
        }
        return event;
    }

    static CreateInvoice.Command toCommand(ReservationConfirmed event) {
        Money dailyRate = new Money(
                event.dailyRate().amount(),
                event.dailyRate().currency());
        InvoiceLine line = InvoiceLine.rentalDays(
                "Aluguel de veículo " + event.licensePlate(),
                LocalDate.parse(Objects.requireNonNull(event.from())),
                LocalDate.parse(Objects.requireNonNull(event.to())),
                dailyRate);
        return new CreateInvoice.Command(
                event.customerId(),
                event.reservationId(),
                List.of(line));
    }
}
