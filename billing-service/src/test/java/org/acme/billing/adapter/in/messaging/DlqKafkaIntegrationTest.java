package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.apache.kafka.common.header.Header;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class DlqKafkaIntegrationTest {

    private static final String SOURCE_TOPIC = "dlq-test";
    private static final String RETRY_1_TOPIC = "dlq-test-retry_50";
    private static final String RETRY_2_TOPIC = "dlq-test-retry_100";
    private static final String RETRY_3_TOPIC = "dlq-test-retry_200";
    private static final String DLQ_TOPIC = "dlq-test-dlq";
    private static final String CORRUPT_KEY = "corrupt-key";
    private static final String CORRUPT_PAYLOAD =
            "{\"version\":1,\"occurredAt\":\"2026-09-23T20:00:00Z\",\"licensePlate\":\"ABC-123\"}";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    DlqTestConsumer consumer;

    @ConfigProperty(name = "mp.messaging.incoming.vehicle-registered-in.dead-letter-queue.topic")
    String realChannelDeadLetterTopic;

    @BeforeEach
    void setUp() {
        consumer.reset();
        createTopicIfMissing(SOURCE_TOPIC);
        createTopicIfMissing(RETRY_1_TOPIC);
        createTopicIfMissing(RETRY_2_TOPIC);
        createTopicIfMissing(RETRY_3_TOPIC);
        createTopicIfMissing(DLQ_TOPIC);
    }

    private void createTopicIfMissing(String topic) {
        try {
            companion.topics().createAndWait(topic, 1);
        } catch (TopicExistsException ignored) {
            // Topic already exists from another test in this test class.
        }
    }

    @Test
    void shouldConfigureDeadLetterTopicOnRealVehicleRegisteredChannel() {
        assertEquals("vehicle-registered-dlq", realChannelDeadLetterTopic,
                "the real channel must route exhausted records to the DLQ instead of dropping them (ADR 003)");
    }

    @Test
    void shouldSendCorruptVehicleRegisteredRecordToRealDeadLetterAfterRetriesAreExhausted()
            throws Exception {
        String key = "real-dlq-" + System.nanoTime();
        String payload = "{\"version\":1,\"occurredAt\":\"2026-09-23T20:00:00Z\",\"licensePlate\":\"REAL-DLQ\"}";

        companion.produceStrings()
                .fromRecords(new ProducerRecord<>(
                        "vehicle-registered",
                        key,
                        payload))
                .awaitCompletion();

        var dlqRecords = companion.consumeStrings()
                .fromTopics("vehicle-registered-dlq", 1, Duration.ofSeconds(15))
                .awaitRecords(1)
                .getRecords();

        var record = dlqRecords.stream()
                .filter(candidate -> key.equals(candidate.key()))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "expected the real vehicle-registered record to reach vehicle-registered-dlq"));

        assertEquals(payload, record.value(),
                "the real DLQ record must preserve the original payload");

        Iterable<Header> originalTopics = record.headers().headers("delayed-retry-topic");
        String originalTopic = originalTopics.iterator().hasNext()
                ? new String(originalTopics.iterator().next().value(), StandardCharsets.UTF_8)
                : "";
        assertEquals("vehicle-registered-retry_200", originalTopic,
                "the real DLQ record must carry the last delayed-retry topic");

        Iterable<Header> exceptionClasses = record.headers().headers("delayed-retry-exception-class-name");
        String exceptionClass = exceptionClasses.iterator().hasNext()
                ? new String(exceptionClasses.iterator().next().value(), StandardCharsets.UTF_8)
                : "";
        assertEquals("java.lang.IllegalArgumentException", exceptionClass,
                "the real DLQ record must carry the last failure exception class");
    }

    @Test
    void shouldSendCorruptRecordToDeadLetterAfterRetriesAreExhausted()
            throws Exception {
        companion.produceStrings()
                .fromRecords(new ProducerRecord<>(
                        SOURCE_TOPIC,
                        CORRUPT_KEY,
                        CORRUPT_PAYLOAD))
                .awaitCompletion();

        consumer.exhausted().get(10, TimeUnit.SECONDS);

        assertEquals(4, consumer.attempts(),
                "the record must be retried max-retries times before reaching the dead-letter topic");

        boolean retriedAfterDeadLetter;
        try {
            consumer.unexpectedAttempt().get(300, TimeUnit.MILLISECONDS);
            retriedAfterDeadLetter = true;
        } catch (java.util.concurrent.TimeoutException expected) {
            retriedAfterDeadLetter = false;
        }
        assertFalse(retriedAfterDeadLetter,
                "expected no delivery attempt after the record reached the dead-letter topic");
        assertEquals(4, consumer.attempts());

        var dlqRecords = companion.consumeStrings()
                .fromTopics(DLQ_TOPIC, 1, Duration.ofSeconds(10))
                .awaitRecords(1)
                .getRecords();

        assertEquals(1, dlqRecords.size(),
                "expected the exhausted record to land on the dead-letter topic");
        assertEquals(CORRUPT_KEY, dlqRecords.get(0).key(),
                "the dead-letter record must preserve the original key");
        assertEquals(CORRUPT_PAYLOAD, dlqRecords.get(0).value(),
                "the dead-letter record must preserve the original payload");

        Iterable<Header> reasons = dlqRecords.get(0).headers().headers("delayed-retry-reason");
        String reason = reasons.iterator().hasNext()
                ? new String(reasons.iterator().next().value(), StandardCharsets.UTF_8)
                : "";
        assertFalse(reason.isEmpty(), "expected a delayed-retry-reason header on the dead-letter record");
        assertTrue(reason.contains("missing required fields"),
                "expected the corrupt-event reason, but was: " + reason);

        Iterable<Header> originalTopics = dlqRecords.get(0).headers().headers("delayed-retry-topic");
        String originalTopic = originalTopics.iterator().hasNext()
                ? new String(originalTopics.iterator().next().value(), StandardCharsets.UTF_8)
                : "";
        assertFalse(originalTopic.isEmpty(), "expected a delayed-retry-topic header on the dead-letter record");
        assertEquals(RETRY_3_TOPIC, originalTopic,
                "the delayed-retry-topic header must point to the topic the record was last consumed from");

        assertTrue(dlqRecords.get(0).headers().headers("delayed-retry-count").iterator().hasNext(),
                "expected a delayed-retry-count header on the dead-letter record");

        Iterable<Header> exceptionClasses = dlqRecords.get(0).headers().headers("delayed-retry-exception-class-name");
        String exceptionClass = exceptionClasses.iterator().hasNext()
                ? new String(exceptionClasses.iterator().next().value(), StandardCharsets.UTF_8)
                : "";
        assertEquals("java.lang.IllegalArgumentException", exceptionClass,
                "the dead-letter record must carry the last failure exception class");
    }
}