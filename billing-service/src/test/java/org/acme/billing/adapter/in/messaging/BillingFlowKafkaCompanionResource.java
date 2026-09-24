package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.kafka.KafkaCompanionResource;

import java.util.Map;

public class BillingFlowKafkaCompanionResource extends KafkaCompanionResource {

    @Override
    public Map<String, String> start() {
        Map<String, String> props = super.start();
        if (kafkaCompanion != null) {
            createTopicIfMissing("reservation-confirmed");
            createTopicIfMissing("reservation-confirmed-retry_1000");
            createTopicIfMissing("reservation-confirmed-retry_5000");
            createTopicIfMissing("reservation-confirmed-retry_15000");
            createTopicIfMissing("reservation-confirmed-dlq");
            createTopicIfMissing("rental-completed");
            createTopicIfMissing("rental-completed-retry_1000");
            createTopicIfMissing("rental-completed-retry_5000");
            createTopicIfMissing("rental-completed-retry_15000");
            createTopicIfMissing("rental-completed-dlq");
        }
        return props;
    }

    private void createTopicIfMissing(String topic) {
        try {
            kafkaCompanion.topics().createAndWait(topic, 1);
        } catch (org.apache.kafka.common.errors.TopicExistsException ignored) {
            // Topic already exists.
        }
    }
}