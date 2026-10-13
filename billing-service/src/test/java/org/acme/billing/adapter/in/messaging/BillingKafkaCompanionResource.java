package org.acme.billing.adapter.in.messaging;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.model.Network.Ipam;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.ResourceReaper;

import java.util.HashMap;
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
 *
 * <p>The network carries a deterministic name ({@value #KAFKA_NETWORK_NAME}) and is created only when missing.
 * Testcontainers' {@link Network} builder generates a random name per {@link Network} instance, and the
 * config-evidence tests of the cap.11 (item 11.1) boot the same jar under the {@code kubernetes} profile in a
 * second Quarkus application while the first one is still running — that restarts this global resource and the
 * new network would create a second, differently-named network on the same {@value #KAFKA_SUBNET}, which Docker
 * rejects with {@code 403 Pool overlaps}. A named network that already exists is reused through
 * {@code networkMode = name}, so the broker of the second application joins the same pinned network. The network
 * is labelled like any testcontainers-managed resource, so Docker's ResourceReaper removes it at the end of the
 * run.</p>
 */
public class BillingKafkaCompanionResource extends KafkaCompanionResource {

    private static final String KAFKA_SUBNET = "172.29.0.0/16";
    private static final String KAFKA_GATEWAY = "172.29.0.1";
    private static final String KAFKA_NETWORK_NAME = "acme-car-rental-billing-kafka";

    /**
     * A {@link Network} whose {@code id} is the deterministic Docker network name. Testcontainers uses the id as
     * the container's {@code networkMode}; because the id is already set it never attempts to create the network.
     */
    private static final Network KAFKA_NETWORK = new Network() {
        @Override
        public String getId() {
            return KAFKA_NETWORK_NAME;
        }

        @Override
        public void close() {
            // The network is shared and deterministically named; ResourceReaper removes it after the run.
        }
    };

    private static void createKafkaNetworkIfMissing() {
        DockerClient docker = DockerClientFactory.lazyClient();
        com.github.dockerjava.api.model.Network existing = docker.listNetworksCmd().exec().stream()
                .filter(network -> KAFKA_NETWORK_NAME.equals(network.getName()))
                .findFirst()
                .orElse(null);
        if (existing != null) {
            String subnet = firstSubnet(existing.getIpam());
            if (!KAFKA_SUBNET.equals(subnet)) {
                if (existing.getContainers() == null || existing.getContainers().isEmpty()) {
                    docker.removeNetworkCmd(KAFKA_NETWORK_NAME).exec();
                } else {
                    throw new IllegalStateException("Network " + KAFKA_NETWORK_NAME + " is pinned to subnet "
                            + subnet + " but " + KAFKA_SUBNET + " is expected and the network is in use");
                }
            } else {
                return;
            }
        }
        CreateNetworkCmd create = docker.createNetworkCmd()
                .withName(KAFKA_NETWORK_NAME)
                .withDriver("bridge")
                .withCheckDuplicate(true)
                .withIpam(new Ipam()
                        .withConfig(new Ipam.Config()
                                .withSubnet(KAFKA_SUBNET)
                                .withGateway(KAFKA_GATEWAY)));
        Map<String, String> labels = new HashMap<>(DockerClientFactory.DEFAULT_LABELS);
        labels.putAll(ResourceReaper.instance().getLabels());
        create.withLabels(labels).exec();
    }

    private static String firstSubnet(Ipam ipam) {
        if (ipam == null || ipam.getConfig() == null || ipam.getConfig().isEmpty()) {
            return null;
        }
        return ipam.getConfig().get(0).getSubnet();
    }

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
        createKafkaNetworkIfMissing();
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