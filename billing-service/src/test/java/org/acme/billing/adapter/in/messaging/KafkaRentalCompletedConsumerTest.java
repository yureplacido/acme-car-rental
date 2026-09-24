package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Uni;
import org.acme.billing.application.event.RentalCompleted;
import org.acme.billing.application.usecase.OpenInvoiceForRental;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaRentalCompletedConsumerTest {

    private final ObjectMapper mapper = new ObjectMapper().findAndRegisterModules();

    @Test
    void shouldConsumeWellFormedRentalCompletedEventPayload() throws Exception {
        AtomicReference<RentalCompleted> received = new AtomicReference<>();
        KafkaRentalCompletedConsumer consumer =
                new KafkaRentalCompletedConsumer(
                        mapper,
                        event -> {
                            received.set(event);
                            return Uni.createFrom().voidItem();
                        });

        RentalCompleted event = rentalCompleted();

        consumer.consume(mapper.writeValueAsString(event)).await().atMost(Duration.ofSeconds(5));

        assertEquals(event, received.get());
    }

    @Test
    void shouldMapEventToOpenInvoiceCommand() {
        RentalCompleted event = rentalCompleted();

        OpenInvoiceForRental.Command command = KafkaRentalCompletedConsumer.toCommand(event);

        assertEquals("reservation-1", command.details().reservationId());
        assertEquals(LocalDate.of(2026, 11, 1), command.details().startDate());
        assertEquals(LocalDate.of(2026, 11, 3), command.details().endDate());
        assertEquals(new BigDecimal("200.00"), command.details().dailyRate().amount());
        assertEquals("BRL", command.details().dailyRate().currency());
        assertEquals("FLOW-1", command.details().licensePlate());
    }

    @Test
    void shouldFailWhenPayloadCannotBeDeserialized() {
        KafkaRentalCompletedConsumer consumer =
                new KafkaRentalCompletedConsumer(
                        mapper,
                        event -> Uni.createFrom().voidItem());

        IllegalArgumentException error = assertThrows(
                IllegalArgumentException.class,
                () -> consumer.consume("not-json").await().atMost(Duration.ofSeconds(5)));

        assertTrue(error.getMessage().contains("Could not deserialize RentalCompleted event"));
    }

    @Test
    void shouldFailWhenDeserializedEventIsMissingRequiredFields() {
        KafkaRentalCompletedConsumer consumer =
                new KafkaRentalCompletedConsumer(
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

    private RentalCompleted rentalCompleted() {
        return new RentalCompleted(
                UUID.randomUUID(),
                1,
                Instant.parse("2026-09-21T12:00:00Z"),
                "rental-1",
                "reservation-1",
                "alice",
                7L,
                "FLOW-1",
                LocalDate.of(2026, 11, 1),
                LocalDate.of(2026, 11, 3),
                new RentalCompleted.Money(new BigDecimal("200.00"), "BRL"));
    }
}