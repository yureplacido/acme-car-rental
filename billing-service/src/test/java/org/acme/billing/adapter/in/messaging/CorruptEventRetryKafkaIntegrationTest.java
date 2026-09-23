package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.errors.TopicExistsException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import jakarta.inject.Inject;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class CorruptEventRetryKafkaIntegrationTest {

    private static final String SOURCE_TOPIC = "corrupt-test";
    private static final String CORRUPT_PAYLOAD =
            "{\"version\":1,\"occurredAt\":\"2026-09-23T20:00:00Z\",\"licensePlate\":\"ABC-123\"}";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    CorruptEventTestConsumer consumer;

    @BeforeEach
    void resetFixtures() {
        consumer.reset();
        createTopicIfMissing(SOURCE_TOPIC);
        createTopicIfMissing("corrupt-test-retry_1000");
        createTopicIfMissing("corrupt-test-retry_5000");
        createTopicIfMissing("corrupt-test-retry_15000");
    }

    private void createTopicIfMissing(String topic) {
        try {
            companion.topics().createAndWait(topic, 1);
        } catch (TopicExistsException ignored) {
            // Topic already exists from a previous test run.
        }
    }

    @Test
    void shouldRouteCorruptEventsThroughDelayedRetryBeforeAbandoning()
            throws Exception {
        companion.produceStrings()
                .fromRecords(new ProducerRecord<>(
                        SOURCE_TOPIC,
                        "corrupt-key",
                        CORRUPT_PAYLOAD))
                .awaitCompletion();

        consumer.exhausted().get(60, TimeUnit.SECONDS);

        assertEquals(4, consumer.attempts(),
                "corrupt event must be redelivered through the retry topics before being abandoned");

        boolean retriedWithinQuietPeriod;
        try {
            consumer.unexpectedAttempt().get(2, TimeUnit.SECONDS);
            retriedWithinQuietPeriod = true;
        } catch (TimeoutException expected) {
            retriedWithinQuietPeriod = false;
        }

        assertFalse(retriedWithinQuietPeriod,
                "expected no retry after exhaustion, but a further attempt occurred");
        assertEquals(4, consumer.attempts());
    }
}