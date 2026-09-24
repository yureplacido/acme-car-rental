package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.ReservationConfirmed;
import org.acme.billing.application.usecase.CreateInvoice;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaReservationConfirmedConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldConsumeWellFormedReservationConfirmedEventPayload() throws Exception {
        AtomicReference<ReservationConfirmed> received = new AtomicReference<>();
        KafkaReservationConfirmedConsumer consumer =
                new KafkaReservationConfirmedConsumer(
                        mapper,
                        event -> {
                            received.set(event);
                            return Uni.createFrom().voidItem();
                        });

        ReservationConfirmed event = reservationConfirmed();

        consumer.consume(mapper.writeValueAsString(event)).await().atMost(Duration.ofSeconds(5));

        assertEquals(event, received.get());
    }

    @Test
    void shouldMapEventToCreateInvoiceCommand() {
        ReservationConfirmed event = reservationConfirmed();

        CreateInvoice.Command command = KafkaReservationConfirmedConsumer.toCommand(event);

        assertEquals("alice", command.customerId());
        assertEquals("reservation-1", command.reservationId());
        assertEquals(1, command.lines().size());
        assertEquals("Aluguel de veículo FLOW-1", command.lines().get(0).description());
        assertEquals(3, command.lines().get(0).quantity());
        assertEquals(new BigDecimal("200.00"), command.lines().get(0).unitPrice().amount());
        assertEquals("BRL", command.lines().get(0).unitPrice().currency());
    }

    @Test
    void shouldFailWhenPayloadCannotBeDeserialized() {
        KafkaReservationConfirmedConsumer consumer =
                new KafkaReservationConfirmedConsumer(
                        mapper,
                        event -> Uni.createFrom().voidItem());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume("not-json").await().atMost(Duration.ofSeconds(5)));

        assertTrue(error.getMessage().contains("Could not deserialize ReservationConfirmed event"));
    }

    @Test
    void shouldFailWhenDeserializedEventIsMissingRequiredFields() {
        KafkaReservationConfirmedConsumer consumer =
                new KafkaReservationConfirmedConsumer(
                        mapper,
                        event -> Uni.createFrom().voidItem());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume(
                        "{\"version\":1,\"occurredAt\":\"2026-09-23T10:00:00Z\","
                                + "\"customerId\":\"alice\",\"licensePlate\":\"FLOW-1\"}")
                        .await()
                        .atMost(Duration.ofSeconds(5)));

        assertTrue(error.getMessage().contains("missing required fields"));
    }

    private ReservationConfirmed reservationConfirmed() {
        return new ReservationConfirmed(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                "reservation-1",
                "alice",
                7L,
                "FLOW-1",
                "2026-11-01",
                "2026-11-03",
                new ReservationConfirmed.Money(new BigDecimal("200.00"), "BRL"));
    }
}