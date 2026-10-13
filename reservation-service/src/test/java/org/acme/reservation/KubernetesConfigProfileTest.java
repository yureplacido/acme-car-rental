package org.acme.reservation;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cap.11 item 11.1 - evidencia de config externalizada por ambiente: o MESMO jar
 * (este artefato) boota com o config profile {@code kubernetes} ({@link
 * KubernetesConfigProfile}) e resolve os providers para os targets do cluster, sem
 * rebuild.
 *
 * <p>O livro (11.1.1 "Externalizing providers for independent deployment") externaliza
 * os providers de dentro da aplicacao para containers/ambientes; aqui o analogo e o
 * runtime por {@code QUARKUS_PROFILE}: datasource, OIDC, GraphQL e discovery apontam
 * para os colaboradores in-cluster ({@code *-postgres}, {@code keycloak}, nomes de
 * Service). O par dessa evidencia e o smoke do compose (ADR 011): a mesma imagem em
 * {@code %docker} fala com Consul/localhost. Dois ambientes, mesma imagem, sem rebuild.</p>
 *
 * <p>Nao verifica conectividade (nao ha cluster na JVM de teste): verifica a RESOLUCAO
 * da config, que e onde o rebuild seria necessario se os providers fossem fixos. A
 * prova de pontos vivos fica para o item 11.6 (deploy real).</p>
 */
@QuarkusTest
@TestProfile(KubernetesConfigProfile.class)
class KubernetesConfigProfileTest {

    @Inject
    SmallRyeConfig config;

    @ConfigProperty(name = "quarkus.datasource.reactive.url")
    String datasourceUrl;

    @ConfigProperty(name = "quarkus.smallrye-graphql-client.inventory.url")
    String inventoryGraphqlUrl;

    @Test
    void resolvesKubernetesDatasourceEndpoint() {
        assertEquals("vertx-reactive:postgresql://reservation-postgres:5432/reservation",
                datasourceUrl);
    }

    @Test
    void resolvesKubernetesGraphQlEndpoint() {
        assertEquals("http://inventory-service:8083/graphql", inventoryGraphqlUrl);
    }

    @Test
    void resolvesKubernetesOidcEndpoint() {
        assertEquals("http://keycloak:8080/realms/car-rental",
                config.getValue("quarkus.oidc.auth-server-url", String.class));
        assertEquals("reservation-service",
                config.getValue("quarkus.oidc.client-id", String.class));
    }

    @Test
    void resolvesKubernetesServiceDiscovery() {
        assertEquals("kubernetes",
                config.getValue("quarkus.stork.rentals.service-discovery.type", String.class));
        assertTrue(config.getOptionalValue(
                "quarkus.stork.rentals.service-discovery.k8s-namespace", String.class)
                .orElse("").equals("default"));
        assertEquals("false", config.getValue(
                "acme.consul.registration.enabled", String.class));
    }
}