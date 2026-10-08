package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.kafka.KafkaCompanionResource;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.testcontainers.containers.Network;

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
 *
 * <p>The Strimzi broker is pinned to a dedicated testcontainers network with a deterministic subnet
 * ({@value #KAFKA_SUBNET}) instead of the default {@code Network.SHARED}. Docker allocates the shared network
 * the first free {@code /16} after the bridge default (172.17 → 172.18), and corporate VPNs/originate static
 * routes that hijack these RFC1918 ranges (measured on 2026-10-08: 172.18.0.0/16 and 172.31.0.0/16 were
 * routed into the VPN tunnel). With the broker inside such a range, the host-to-container forwarding for the
 * published Kafka port dies and the AdminClient fails in {@code fetchMetadata}. A pinned subnet leaves the
 * suite independent of the machine's route table; billing uses {@value #KAFKA_SUBNET} and inventory-service
 * uses the next {@code /16} so the two Kafka suites can run in parallel without overlapping.
 */
public class BillingKafkaCompanionResource extends KafkaCompanionResource {

    private static final String KAFKA_SUBNET = "172.29.0.0/16";
    private static final String KAFKA_GATEWAY = "172.29.0.1";

    private static final Network KAFKA_NETWORK = Network.builder()
            .createNetworkCmdModifier(cmd -> cmd.withIpam(new com.github.dockerjava.api.model.Network.Ipam()
                    .withConfig(new com.github.dockerjava.api.model.Network.Ipam.Config()
                            .withSubnet(KAFKA_SUBNET)
                            .withGateway(KAFKA_GATEWAY))))
            .build();

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
    protected StrimziKafkaContainer createContainer(String imageName) {
        StrimziKafkaContainer container = super.createContainer(imageName);
        container.withNetwork(KAFKA_NETWORK);
        return container;
    }

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
