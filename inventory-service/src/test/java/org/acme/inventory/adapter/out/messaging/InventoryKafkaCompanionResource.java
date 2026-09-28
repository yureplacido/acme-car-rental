package org.acme.inventory.adapter.out.messaging;

import io.quarkus.test.kafka.KafkaCompanionResource;

import java.util.Map;

public class InventoryKafkaCompanionResource extends KafkaCompanionResource {

    @Override
    public Map<String, String> start() {
        Map<String, String> props = super.start();
        if (kafkaCompanion != null && !kafkaCompanion.topics().list().contains("vehicle-registered")) {
            kafkaCompanion.topics().createAndWait("vehicle-registered", 1);
        }
        return props;
    }
}
