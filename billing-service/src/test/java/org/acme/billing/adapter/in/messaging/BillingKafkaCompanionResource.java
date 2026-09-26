package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.kafka.KafkaCompanionResource;

import java.util.List;
import java.util.Map;

/**
 * Single Kafka companion for the whole billing test suite.
 *
 * <p>It must be declared with {@code restrictToAnnotatedClass = false} by every Kafka integration test so that
 * exactly one broker is created for the entire run and the same {@code KafkaCompanion} is injected everywhere.
 * Two companion resources would each start their own broker while the application only gets the bootstrap
 * servers of one of them, and every topic created on the other broker would be invisible to the application.
 *
 * <p>Topics are pre-created here, on the broker the application is going to use, so tests never depend on
 * broker-side topic auto-creation. That matters for {@code invoice-opened}: nothing publishes to it until the
 * outbox relay runs, so a test that needs to read its end offset first has to find the topic already there.
 */
public class BillingKafkaCompanionResource extends KafkaCompanionResource {

    private static final List<String> TOPICS = List.of(
            "vehicle-registered",
            "vehicle-registered-retry_50",
            "vehicle-registered-retry_100",
            "vehicle-registered-retry_200",
            "vehicle-registered-dlq",
            "reservation-confirmed",
            "reservation-confirmed-retry_50",
            "reservation-confirmed-retry_100",
            "reservation-confirmed-retry_200",
            "reservation-confirmed-dlq",
            "rental-completed",
            "rental-completed-retry_50",
            "rental-completed-retry_100",
            "rental-completed-retry_200",
            "rental-completed-dlq",
            "invoice-opened");

    @Override
    public Map<String, String> start() {
        Map<String, String> props = super.start();
        if (kafkaCompanion != null) {
            for (String topic : TOPICS) {
                createTopicIfMissing(topic);
            }
        }
        return props;
    }

    private void createTopicIfMissing(String topic) {
        if (kafkaCompanion.topics().list().contains(topic)) {
            return;
        }
        kafkaCompanion.topics().createAndWait(topic, 1);
    }
}
