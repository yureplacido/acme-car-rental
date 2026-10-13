package org.acme.users;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cap.11 item 11.1 - a MESMA imagem (users-service) boota sob o config profile
 * {@code kubernetes} ({@link KubernetesConfigProfile}) e resolve os providers do
 * cluster sem rebuild: o Keycloak (OIDC) e a descoberta Stork via Kubernetes. O par
 * dessa evidencia e o smoke do compose (ADR 011): a mesma imagem em {@code %docker}
 * fala com {@code keycloak:8080} e o Consul; em cluster o Keycloak tem o mesmo nome
 * e a descoberta usa a API do Kubernetes.
 */
@QuarkusTest
@TestProfile(KubernetesConfigProfile.class)
class KubernetesConfigProfileTest {

    @Inject
    SmallRyeConfig config;

    @Test
    void resolvesKubernetesOidcEndpoint() {
        assertEquals("http://keycloak:8080/realms/car-rental",
                config.getValue("quarkus.oidc.auth-server-url", String.class));
        assertEquals("users-service",
                config.getValue("quarkus.oidc.client-id", String.class));
    }

    @Test
    void resolvesKubernetesServiceDiscovery() {
        assertEquals("kubernetes",
                config.getValue("quarkus.stork.reservations.service-discovery.type", String.class));
        assertTrue(config.getOptionalValue(
                "quarkus.stork.reservations.service-discovery.k8s-namespace", String.class)
                .orElse("").equals("default"));
    }
}