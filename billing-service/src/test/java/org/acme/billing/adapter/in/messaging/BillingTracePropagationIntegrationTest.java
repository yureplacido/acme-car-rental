package org.acme.billing.adapter.in.messaging;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cap.10 - tracing ponta a ponta (item 7): o consumidor de {@code vehicle-registered}
 * deve processar o evento sob o trace propagado no header {@code traceparent} do
 * record Kafka (guia Quarkus "Messaging", secao OpenTelemetry Tracing: mensagens
 * de entrada herdam o span do record como pai).
 *
 * <p>O teste publica um record com um {@code traceparent} conhecido (upstream
 * simulado) e verifica no {@link InMemorySpanExporter} que o processamento gerou
 * um span com o traceId do header. O contexto viaja no header, nunca no payload.
 */
@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class BillingTracePropagationIntegrationTest {

    private static final String TOPIC = "vehicle-registered";
    private static final Duration TIMEOUT = Duration.ofSeconds(15);

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    InMemorySpanExporter spanExporter;

    @BeforeEach
    void resetExporter() {
        spanExporter.reset();
    }

    @Test
    void shouldProcessVehicleRegisteredUnderTheTracePropagatedInTheRecordHeader() {
        String traceId = "4bf92f3577b34da6a3ce929d0e0e4736";
        String parentSpanId = "00f067aa0ba902b7";
        String traceparent = "00-" + traceId + "-" + parentSpanId + "-01";

        ProducerRecord<String, String> record = new ProducerRecord<>(TOPIC, "trace-test", validPayload());
        record.headers().add("traceparent", traceparent.getBytes(StandardCharsets.UTF_8));

        companion.produceStrings()
                .fromRecords(record)
                .awaitCompletion();

        SpanData span = awaitConsumerSpanWithTraceId(traceId, TIMEOUT);

        assertNotNull(span, "expected a consumer span under the propagated trace " + traceId);
        assertEquals(traceId, span.getTraceId());
        assertEquals(parentSpanId, span.getParentSpanId(),
                "the consumer span must inherit the record span as parent");
    }

    private String validPayload() {
        return "{\"eventId\":\"" + UUID.randomUUID() + "\",\"version\":1,"
                + "\"occurredAt\":\"2026-09-28T12:00:00Z\","
                + "\"vehicleId\":{\"value\":42},\"licensePlate\":\"TRACE-1\"}";
    }

    private SpanData awaitConsumerSpanWithTraceId(String traceId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            SpanData match = spans.stream()
                    .filter(span -> traceId.equals(span.getTraceId()))
                    .filter(span -> span.getKind() == SpanKind.CONSUMER)
                    .findFirst()
                    .orElse(null);
            if (match != null) {
                return match;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException(e);
            }
        }
        return null;
    }
}