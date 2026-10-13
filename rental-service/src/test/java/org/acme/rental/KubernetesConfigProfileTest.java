package org.acme.rental;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Cap.11 item 11.1 - a MESMA imagem (rental-service) boota sob o config profile
 * {@code kubernetes} ({@link KubernetesConfigProfile}) e resolve o provider do
 * cluster sem rebuild: o MongoDB. O par dessa evidencia e o smoke do compose
 * (ADR 011): a mesma imagem em {@code %docker} fala com {@code rental-mongo:27017};
 * em cluster o mesmo nome aponta para o Service do K8s (o DNS real fica para o 11.6).
 */
@QuarkusTest
@TestProfile(KubernetesConfigProfile.class)
class KubernetesConfigProfileTest {

    @Inject
    SmallRyeConfig config;

    @Test
    void resolvesKubernetesMongoEndpoint() {
        assertEquals("mongodb://rental-mongo:27017",
                config.getValue("quarkus.mongodb.connection-string", String.class));
        assertEquals("rental", config.getValue("quarkus.mongodb.database", String.class));
    }
}