package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cap.10 item 9 - service discovery: a localizacao do rental-service deixa de ser uma
 * URL fixada na configuracao e passa a ser resolvida pelo Stork no Consul como "rentals".
 *
 * E o caminho completo: o REST Client (stork://rentals), ao lado do Stork, resolve a
 * instancia no Consul e chega ao stub registrado - a mesma fronteira que o users-service
 * exercita no dele (que carrega @AccessToken e nao sai sem request context autenticado).
 *
 * Tag "consul": com o Maven profile kubernetes ({@code -P kubernetes}) esta classe e a
 * contraposicao dela (RentalServiceDiscoveryKubernetesTest) sao trocadas pela suite.
 */
@QuarkusTest
@Tag("consul")
@QuarkusTestResource(value = ConsulRentalDiscoveryTestResource.class, restrictToAnnotatedClass = true)
class RentalServiceDiscoveryTest {

    @Inject
    @RestClient
    RentalClient client;

    @Test
    void shouldReachRentalThroughTheInstanceResolvedInConsul() {
        RentalResponse response = client.start("alice", 42L).await().indefinitely();

        assertEquals("STARTED", response.status());
        assertEquals(42L, response.reservationId());
        assertEquals("alice", response.customerId());
    }
}