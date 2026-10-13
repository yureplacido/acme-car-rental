package org.acme.inventory.adapter.out.messaging;

import com.github.dockerjava.api.DockerClient;
import com.github.dockerjava.api.command.CreateNetworkCmd;
import com.github.dockerjava.api.model.Network.Ipam;
import io.quarkus.test.kafka.KafkaCompanionResource;
import io.strimzi.test.container.StrimziKafkaContainer;
import org.testcontainers.DockerClientFactory;
import org.testcontainers.containers.Network;
import org.testcontainers.utility.ResourceReaper;

import java.util.HashMap;
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
 *
 * <p>The network carries a deterministic name ({@value #KAFKA_NETWORK_NAME}) and is created only when missing.
 * Testcontainers' {@link Network} builder generates a random name per {@link NetworkImpl} instance, and the
 * config-evidence tests of the cap.11 (item 11.1) boot the same jar under the {@code kubernetes} profile in a
 * second Quarkus application while the first one is still running — that restarts this global resource and the
 * new {@link Network} would create a second, differently-named network on the same {@value #KAFKA_SUBNET}, which
 * Docker rejects with {@code 403 Pool overlaps}. A named network that already exists is reused through
 * {@code networkMode = name}, so the broker of the second application joins the same pinned network. The network
 * is labelled like any testcontainers-managed resource, so Docker's ResourceReaper removes it at the end of the
 * run.</p>
 */
public class InventoryKafkaCompanionResource extends KafkaCompanionResource {

    private static final String KAFKA_SUBNET = "172.30.0.0/16";
    private static final String KAFKA_GATEWAY = "172.30.0.1";
    private static final String KAFKA_NETWORK_NAME = "acme-car-rental-inventory-kafka";

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
        if (kafkaCompanion != null && !kafkaCompanion.topics().list().contains("vehicle-registered")) {
            kafkaCompanion.topics().createAndWait("vehicle-registered", 1);
        }
        return props;
    }
}