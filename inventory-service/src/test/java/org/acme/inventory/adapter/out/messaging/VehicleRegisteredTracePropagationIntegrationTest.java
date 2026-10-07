package org.acme.inventory.adapter.out.messaging;

import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.apache.kafka.clients.admin.OffsetSpec;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.TopicPartition;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cap.10 - tracing ponta a ponta (item 7): o record {@code vehicle-registered}
 * produzido pela mutation GraphQL {@code register} deve carregar o header
 * {@code traceparent} do trace da requisicao (guia Quarkus "Messaging", secao
 * OpenTelemetry Tracing: mensagens de saida propagam o span corrente).
 *
 * <p>O teste executa a mutation, consome o record do broker e verifica que o
 * header existe, esta bem formado e carrega o traceId de um span do proprio
 * servico (o trace da requisicao). O contexto viaja no header, nunca no payload.
 */
@QuarkusTest
@QuarkusTestResource(value = InventoryKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class VehicleRegisteredTracePropagationIntegrationTest {

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
    void shouldPropagateTheRequestTraceIntoTheKafkaRecordHeader() {
        TopicPartition partition = KafkaCompanion.tp(TOPIC, 0);
        long fromOffset = companion.offsets().get(partition, OffsetSpec.latest()).offset();

        String plate = "TRC" + System.nanoTime();
        
        String graphqlQuery = "mutation { register(input: { plateNumber: \"" + plate
                + "\", manufacturer: \"Ford\", model: \"Mustang\", category: \"SUV\","
                + " year: 2025, color: \"black\", seats: 5, dailyRate: 149.90, currency: \"BRL\" })"
                + " { id plateNumber } }";

        given()
                .contentType("application/json")
                .body(new java.util.HashMap<String, Object>() {{ put("query", graphqlQuery); }})
                .when()
                .post("/graphql")
                .then()
                .statusCode(200)
                .body(containsString("plateNumber"));

        ConsumerRecord<String, String> record = companion.consumeStrings()
                .fromOffsets(
                        Map.of(partition, fromOffset),
                        records -> records.select().where(r -> r.value().contains(plate)))
                .awaitRecords(1, TIMEOUT)
                .getRecords()
                .getFirst();

        String traceparent = headerValue(record, "traceparent");
        assertNotNull(traceparent, "expected the produced record to carry a traceparent header");

        String[] parts = traceparent.split("-");
        assertEquals(4, parts.length, "traceparent must be version-traceId-spanId-flags");
        assertEquals("00", parts[0]);
        assertEquals(32, parts[1].length(), "traceId must be 32 hex chars");
        assertEquals(16, parts[2].length(), "spanId must be 16 hex chars");

        SpanData span = awaitSpanWithTraceId(parts[1], TIMEOUT);
        assertNotNull(span, "expected a span under the trace propagated in the header " + parts[1]);
        assertEquals(parts[1], span.getTraceId());

        SpanData producerSpan = awaitProducerSpanWithTraceId(parts[1], TIMEOUT);
        assertNotNull(producerSpan,
                "expected the produced-record span under the trace " + parts[1]);
        assertEquals(parts[2], producerSpan.getSpanId(),
                "the header spanId must be the span of the produced record");
    }

    private String headerValue(ConsumerRecord<String, String> record, String name) {
        var header = record.headers().lastHeader(name);
        return header == null ? null : new String(header.value(), StandardCharsets.UTF_8);
    }

    private SpanData awaitSpanWithTraceId(String traceId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            SpanData match = spans.stream()
                    .filter(span -> traceId.equals(span.getTraceId()))
                    .filter(span -> span.getKind() == SpanKind.SERVER)
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

    private SpanData awaitProducerSpanWithTraceId(String traceId, Duration timeout) {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (System.nanoTime() < deadline) {
            List<SpanData> spans = spanExporter.getFinishedSpanItems();
            SpanData match = spans.stream()
                    .filter(span -> traceId.equals(span.getTraceId()))
                    .filter(span -> span.getKind() == SpanKind.PRODUCER)
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