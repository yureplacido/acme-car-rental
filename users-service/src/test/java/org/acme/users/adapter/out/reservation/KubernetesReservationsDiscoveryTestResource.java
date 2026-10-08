package org.acme.users.adapter.out.reservation;

import io.fabric8.kubernetes.api.model.EndpointAddressBuilder;
import io.fabric8.kubernetes.api.model.EndpointPortBuilder;
import io.fabric8.kubernetes.api.model.EndpointSubsetBuilder;
import io.fabric8.kubernetes.api.model.EndpointsBuilder;
import io.fabric8.kubernetes.api.model.NamespaceBuilder;
import io.fabric8.kubernetes.api.model.ObjectReferenceBuilder;
import io.fabric8.kubernetes.client.NamespacedKubernetesClient;
import io.quarkus.test.kubernetes.client.KubernetesServerTestResource;

import java.util.HashMap;
import java.util.Map;

/**
 * Cap.10 item 10 (roadmap item 10) - discovery via Kubernetes: o par do
 * {@link ConsulTestResource} para o provider Stork kubernetes.
 *
 * O mock do API server (subido pelo {@link KubernetesServerTestResource}) nao
 * roteia HTTP, entao este resource planta no "cluster" o Endpoints que o
 * reservation-service exporia como Service "reservations" num cluster real; o que
 * se verifica e o contrato de discovery (qual instancia o provider resolve), nao o
 * caminho HTTP completo que o Consul permite.
 */
public class KubernetesReservationsDiscoveryTestResource extends KubernetesServerTestResource {

    static final String NAMESPACE = "car-rental";
    static final String RESERVATIONS_STUB_HOST = "127.0.0.1";
    static final int RESERVATIONS_STUB_PORT = 18083;

    @Override
    public Map<String, String> start() {
        // super.start() sobe o mock e seta kubernetes.master/namespace/trust-all como
        // system properties antes do boot - o provider fabric8 do Stork le esses mesmos
        // (Config.autoConfigure). Aqui forçamos o provider kubernetes e fixamos o resto.
        Map<String, String> config = new HashMap<>(super.start());
        config.put("quarkus.stork.reservations.service-discovery.type", "kubernetes");
        config.put("quarkus.stork.reservations.service-discovery.k8s-host",
                getClient().getConfiguration().getMasterUrl());
        config.put("quarkus.stork.reservations.service-discovery.k8s-namespace", NAMESPACE);
        // O mock nao anuncia o APIGroup discovery.k8s.io; fixar false faz o provider
        // ir pelo caminho de Endpoints (deterministico) em vez de EndpointSlice.
        config.put("quarkus.stork.reservations.service-discovery.use-endpoint-slices", "false");
        return config;
    }

    @Override
    protected void configureServer() {
        NamespacedKubernetesClient client = getClient();
        client.namespaces().resource(new NamespaceBuilder()
                .withNewMetadata().withName(NAMESPACE).endMetadata().build())
                .create();

        // O Endpoints carrega o proprio namespace; criar via client.resource() dispara
        // o POST /api/v1/namespaces/car-rental/endpoints de forma deterministicamente
        // persistida pelo CRUD do mock (a forma com .endpoints().inNamespace()
        // ocasionalmente nao emitia o request).
        client.resource(new EndpointsBuilder()
                .withNewMetadata()
                .withName("reservations")
                .withNamespace(NAMESPACE)
                .endMetadata()
                .withSubsets(new EndpointSubsetBuilder()
                        .withAddresses(new EndpointAddressBuilder()
                                .withIp(RESERVATIONS_STUB_HOST)
                                // o provider Stork usa targetRef para enriquecer com pod;
                                // sem ele, gatherBackendPods NPE ao ler getName().
                                .withTargetRef(new ObjectReferenceBuilder()
                                        .withKind("Pod")
                                        .withName("reservations-stub-0")
                                        .withNamespace(NAMESPACE)
                                        .build())
                                .build())
                        .withPorts(new EndpointPortBuilder()
                                .withName("http")
                                .withPort(RESERVATIONS_STUB_PORT)
                                .withProtocol("TCP")
                                .build())
                        .build())
                .build()).create();
    }
}