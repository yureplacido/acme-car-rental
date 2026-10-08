package org.acme.inventory.adapter.out.messaging;

import io.quarkus.test.kafka.KafkaCompanionResource;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.testcontainers.containers.Network;

import java.util.Map;

/**
 * Single Kafka companion for the inventory Kafka integration tests.
 *
 * <p>The Strimzi broker is pinned to a dedicated testcontainers network with a deterministic subnet
 * ({@value #KAFKA_SUBNET}) instead of the default {@code Network.SHARED}: Docker fills {@code Network.SHARED}
 * with the first free {@code /16} (172.18 after the 172.17 bridge) and corporate VPNs inject static routes that
 * hijack those RFC1918 ranges — the host-to-container forwarding of the published Kafka port dies and the
 * AdminClient fails in {@code fetchMetadata} (measured 2026-10-08). inventory-service uses {@value #KAFKA_SUBNET},
 * one {@code /16} above the subnet of the billing Kafka suite, so the two can run in parallel without overlapping.
 */
public class InventoryKafkaCompanionResource extends KafkaCompanionResource {

    private static final String KAFKA_SUBNET = "172.30.0.0/16";
    private static final String KAFKA_GATEWAY = "172.30.0.1";

    private static final Network KAFKA_NETWORK = Network.builder()
            .createNetworkCmdModifier(cmd -> cmd.withIpam(new com.github.dockerjava.api.model.Network.Ipam()
                    .withConfig(new com.github.dockerjava.api.model.Network.Ipam.Config()
                            .withSubnet(KAFKA_SUBNET)
                            .withGateway(KAFKA_GATEWAY))))
            .build();

    @Override
    protected StrimziKafkaContainer createContainer(String imageName) {
        StrimziKafkaContainer container = super.createContainer(imageName);
        container.withNetwork(KAFKA_NETWORK);
        return container;
    }

    @Override
    public Map<String, String> start() {
        Map<String, String> props = super.start();
        if (kafkaCompanion != null && !kafkaCompanion.topics().list().contains("vehicle-registered")) {
            kafkaCompanion.topics().createAndWait("vehicle-registered", 1);
        }
        return props;
    }
}