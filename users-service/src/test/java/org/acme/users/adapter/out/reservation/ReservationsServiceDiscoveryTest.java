package org.acme.users.adapter.out.reservation;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.stork.Stork;
import io.smallrye.stork.api.Service;
import io.smallrye.stork.api.ServiceInstance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.Config;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cap.10 item 9 - service discovery: a localizacao do reservation-service deixa de ser
 * uma URL fixada na configuracao do cliente e passa a ser resolvida pelo Stork no Consul.
 *
 * A chamada real via {@code ReservationsClient} nao e exercitada aqui: o cliente leva
 * {@code @AccessToken} e a propagacao sem token de uma requisicao autenticada aborta com
 * 401 antes de sair do app. O caminho completo (REST client -> Stork -> Consul -> alvo)
 * fica comprovado no reservation-service, cujo cliente de saude nao propaga token.
 *
 * Tag "consul": a descoberta via Consul so faz sentido com esse backend; com o Maven
 * profile kubernetes esta classe fica de fora da suite.
 */
@QuarkusTest
@Tag("consul")
@QuarkusTestResource(value = ConsulTestResource.class, restrictToAnnotatedClass = true)
class ReservationsServiceDiscoveryTest {

    @Inject
    Config config;

    @Test
    void shouldConfigureTheClientForDiscoveryInsteadOfAServiceUrl() {
        assertEquals("stork://reservations",
                config.getValue("quarkus.rest-client.reservations.url", String.class));
        assertEquals("consul",
                config.getOptionalValue("quarkus.stork.reservations.service-discovery.type", String.class)
                        .orElse(null));
        assertTrue(
                config.getOptionalValue("quarkus.rest-client.reservations.url", String.class)
                        .filter(url -> url.startsWith("http"))
                        .isEmpty(),
                "a URL nao pode mais apontar para um host: a localizacao vem da discovery");
    }

    @Test
    void shouldResolveTheReservationInstanceRegisteredInConsul() throws IOException, InterruptedException {
        Service reservations = Stork.getInstance().getService("reservations");
        List<ServiceInstance> instances = reservations.getInstances().await().indefinitely();

        assertEquals(1, instances.size(),
                "o Consul conhece exatamente a instancia registrada pelo test resource");
        ServiceInstance instance = instances.get(0);
        assertEquals("127.0.0.1", instance.getHost());
        assertEquals(config.getValue("acme.test.reservations.stub-port", int.class),
                instance.getPort(), "a instancia resolvida e o stub do teste");

        HttpResponse<String> response = HttpClient.newHttpClient()
                .send(HttpRequest.newBuilder()
                        .uri(URI.create("http://" + instance.getHost() + ":"
                                + instance.getPort() + "/reservations/all"))
                        .GET()
                        .build(), HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("alice"),
                "o endereco que a discovery devolve responde com os dados do reservatorio");
    }
}