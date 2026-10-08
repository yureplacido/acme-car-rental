package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.ServiceInstance;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cap.10 item 10 (roadmap item 10) - service discovery via Kubernetes: a contraparte
 * do {@link RentalServiceDiscoveryTest} (Consul) para o provider Stork kubernetes,
 * selecionada pelo Maven profile kubernetes ({@code -P kubernetes}).
 *
 * O mock do API server nao roteia HTTP, entao aqui nao se chama o REST Client: o
 * contrato verificado e o da discovery - o provider devolve a instancia plantada no
 * Endpoints "rentals". O caminho HTTP completo fica comprovado no teste Consul, cujo
 * backend (Consul real) aceita o stub da JVM como endereco alcancavel.
 */
@QuarkusTest
@Tag("kubernetes")
@QuarkusTestResource(value = KubernetesRentalDiscoveryTestResource.class, restrictToAnnotatedClass = true)
@Timeout(30)
class RentalServiceDiscoveryKubernetesTest {

    @Test
    void shouldResolveTheRentalEndpointFromTheKubernetesService() {
        List<ServiceInstance> instances = Stork.getInstance()
                .getService("rentals")
                .getInstances().await().indefinitely();

        assertEquals(1, instances.size(),
                "o Endpoints 'rentals' tem exatamente o stub do teste");
        ServiceInstance instance = instances.get(0);
        assertEquals(KubernetesRentalDiscoveryTestResource.RENTAL_STUB_HOST, instance.getHost());
        assertEquals(KubernetesRentalDiscoveryTestResource.RENTAL_STUB_PORT, instance.getPort());
    }
}