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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class DelayedRetryKafkaIntegrationTest {

    private static final String SOURCE_TOPIC = "retry-test";
    private static final String RETRY_1_TOPIC = "retry-test-retry-1000";
    private static final String RETRY_2_TOPIC = "retry-test-retry-5000";
    private static final String RETRY_3_TOPIC = "retry-test-retry-15000";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    ObjectMapper objectMapper;

    @Inject
    DelayedRetryTestConsumer consumer;

    @BeforeEach
    void createTopics() {
        companion.topics().createAndWait(SOURCE_TOPIC, 1);
        companion.topics().createAndWait(RETRY_1_TOPIC, 1);
        companion.topics().createAndWait(RETRY_2_TOPIC, 1);
        companion.topics().createAndWait(RETRY_3_TOPIC, 1);
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
                .fromRecords(new ProducerRecord<>(SOURCE_TOPIC, event.vehicleId().value().toString(), payload))
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
}
