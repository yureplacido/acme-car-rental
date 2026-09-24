package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.quarkus.test.kafka.KafkaCompanionResource;
import org.acme.billing.application.event.VehicleRegistered;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.kafka.common.errors.TopicExistsException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class DelayedRetryKafkaIntegrationTest {

    private static final String SOURCE_TOPIC = "retry-test";
    private static final String RETRY_1_TOPIC = "retry-test-retry_1000";
    private static final String RETRY_2_TOPIC = "retry-test-retry_5000";
    private static final String RETRY_3_TOPIC = "retry-test-retry_15000";
    private static final String EXHAUSTION_SOURCE_TOPIC = "retry-exhaustion";
    private static final String EXHAUSTION_RETRY_1_TOPIC = "retry-exhaustion-retry_1000";
    private static final String EXHAUSTION_RETRY_2_TOPIC = "retry-exhaustion-retry_5000";
    private static final String EXHAUSTION_RETRY_3_TOPIC = "retry-exhaustion-retry_15000";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DelayedRetryTestConsumer consumer;

    @Inject
    DelayedRetryExhaustionTestConsumer exhaustionConsumer;

    @BeforeEach
    void resetFixtures() {
        consumer.reset();
        exhaustionConsumer.reset();
        createTopicIfMissing(SOURCE_TOPIC);
        createTopicIfMissing(RETRY_1_TOPIC);
        createTopicIfMissing(RETRY_2_TOPIC);
        createTopicIfMissing(RETRY_3_TOPIC);
        createTopicIfMissing(EXHAUSTION_SOURCE_TOPIC);
        createTopicIfMissing(EXHAUSTION_RETRY_1_TOPIC);
        createTopicIfMissing(EXHAUSTION_RETRY_2_TOPIC);
        createTopicIfMissing(EXHAUSTION_RETRY_3_TOPIC);
    }

    private void createTopicIfMissing(String topic) {
        try {
            companion.topics().createAndWait(topic, 1);
        } catch (TopicExistsException ignored) {
            // Topic already exists from another test in this test class.
        }
    }

    @Test
    void shouldRedeliverThroughConfiguredRetryTopicsUntilSuccess()
            throws Exception {
        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.now(),
                new VehicleRegistered.VehicleId(100L),
                "RET123");

        String payload = objectMapper.writeValueAsString(event);

        companion.produceStrings()
                .fromRecords(new ProducerRecord<>(SOURCE_TOPIC, String.valueOf(event.vehicleId().value()), payload))
                .awaitCompletion();

        consumer.success().get(30, TimeUnit.SECONDS);

        assertEquals(3, consumer.attempts());

        long firstDelayMillis = Duration.between(
                consumer.firstAttemptAt(),
                consumer.secondAttemptAt()).toMillis();

        long secondDelayMillis = Duration.between(
                consumer.secondAttemptAt(),
                consumer.thirdAttemptAt()).toMillis();

        assertTrue(firstDelayMillis >= 800,
                "Expected first retry delay >= 800ms, but was " + firstDelayMillis + "ms");
        assertTrue(secondDelayMillis >= 4000,
                "Expected second retry delay >= 4000ms, but was " + secondDelayMillis + "ms");
    }

    @Test
    void shouldAbandonRecordAfterConfiguredRetriesAreExhausted()
            throws Exception {
        VehicleRegistered event = new VehicleRegistered(
                UUID.randomUUID(),
                1,
                Instant.now(),
                new VehicleRegistered.VehicleId(101L),
                "RET999");

        String payload = objectMapper.writeValueAsString(event);

        companion.produceStrings()
                .fromRecords(new ProducerRecord<>(
                        EXHAUSTION_SOURCE_TOPIC,
                        String.valueOf(event.vehicleId().value()),
                        payload))
                .awaitCompletion();

        exhaustionConsumer.exhausted().get(60, TimeUnit.SECONDS);

        assertEquals(4, exhaustionConsumer.attempts());

        boolean retriedWithinQuietPeriod;
        try {
            exhaustionConsumer.unexpectedAttempt().get(2, TimeUnit.SECONDS);
            retriedWithinQuietPeriod = true;
        } catch (TimeoutException expected) {
            retriedWithinQuietPeriod = false;
        }

        assertFalse(retriedWithinQuietPeriod,
                "expected no retry after exhaustion, but a further attempt occurred");
        assertEquals(4, exhaustionConsumer.attempts());
    }
}
