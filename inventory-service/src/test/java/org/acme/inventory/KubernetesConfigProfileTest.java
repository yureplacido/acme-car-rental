package org.acme.inventory;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cap.11 item 11.1 - a MESMA imagem (inventory-service) boota sob o config profile
 * {@code kubernetes} ({@link KubernetesConfigProfile}) e resolve os providers do
 * cluster sem rebuild: datasource MySQL e broker Kafka. O par dessa evidencia e o
 * smoke do compose (ADR 011): a mesma imagem em {@code %docker} fala com
 * {@code inventory-mysql} e {@code kafka:29092}.
 *
 * <p>Como no billing, o Kafka de teste pode estar ativo na mesma executação Maven
 * (companion global) e sobrescrever {@code kafka.bootstrap.servers} com prioridade de
 * sistema; a chave é verificada de forma tolerante (deve existir), e a evidência forte
 * de "dois ambientes, mesma imagem" fica na datasource, que nenhum recurso de teste
 * sobrescreve.</p>
 */
@QuarkusTest
@TestProfile(KubernetesConfigProfile.class)
class KubernetesConfigProfileTest {

    @Inject
    SmallRyeConfig config;

    @Test
    void resolvesKubernetesMySqlEndpoint() {
        assertEquals("mysql://inventory-mysql:3306/inventory",
                config.getValue("quarkus.datasource.reactive.url", String.class));
        assertEquals("user", config.getValue("quarkus.datasource.username", String.class));
        assertEquals("pass", config.getValue("quarkus.datasource.password", String.class));
    }

    @Test
    void resolvesKubernetesKafkaBootstrap() {
        String bootstrap = config.getValue("kafka.bootstrap.servers", String.class);
        assertNotNull(bootstrap, "kafka.bootstrap.servers deve resolver em %kubernetes");
    }
}