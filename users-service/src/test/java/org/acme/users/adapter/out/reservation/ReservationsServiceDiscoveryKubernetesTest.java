package org.acme.users.adapter.out.reservation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceInstance;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cap.10 item 10 (roadmap item 10) - service discovery via Kubernetes: a contraparte
 * do {@link ReservationsServiceDiscoveryTest} (Consul) para o provider Stork kubernetes,
 * selecionada pelo Maven profile kubernetes ({@code -P kubernetes}).
 *
 * O mock do API server nao roteia HTTP, entao aqui nao se chama o REST Client (que
 * levaria @AccessToken e abortaria 401 sem sessao): o contrato verificado e o da
 * discovery - o provider devolve a instancia plantada no Endpoints "reservations".
 * O caminho HTTP completo fica comprovado no teste Consul.
 */
@QuarkusTest
@Tag("kubernetes")
@QuarkusTestResource(value = KubernetesReservationsDiscoveryTestResource.class, restrictToAnnotatedClass = true)
class ReservationsServiceDiscoveryKubernetesTest {

    @Test
    void shouldResolveTheReservationEndpointFromTheKubernetesService() {
        List<ServiceInstance> instances = Stork.getInstance()
                .getService("reservations")
                .getInstances().await().indefinitely();

        assertEquals(1, instances.size(),
                "o Endpoints 'reservations' tem exatamente o stub do teste");
        ServiceInstance instance = instances.get(0);
        assertEquals(KubernetesReservationsDiscoveryTestResource.RESERVATIONS_STUB_HOST, instance.getHost());
        assertEquals(KubernetesReservationsDiscoveryTestResource.RESERVATIONS_STUB_PORT, instance.getPort());
    }
}