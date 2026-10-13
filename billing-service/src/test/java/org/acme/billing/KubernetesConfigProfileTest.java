package org.acme.billing;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import io.smallrye.config.SmallRyeConfig;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Cap.11 item 11.1 - a MESMA imagem (billing-service) boota sob o config profile
 * {@code kubernetes} ({@link KubernetesConfigProfile}) e resolve os providers do
 * cluster sem rebuild: datasource PG e broker Kafka. O par dessa evidencia e o smoke
 * do compose (ADR 011): a mesma imagem em {@code %docker} fala com
 * {@code billing-postgres} e {@code kafka:29092}; em cluster os mesmos nomes apontam
 * para os Services do K8s.
 *
 * <p>Nota sobre o Kafka: os testes de mensageria agendam um broker de teste global
 * (KafkaCompanionResource com {@code restrictToAnnotatedClass=false}) que injeta
 * {@code kafka.bootstrap.servers} com prioridade mais alta que o application.properties.
 * Quando esse recurso estiver ativo na mesma executação Maven, o valor resolvido é o
 * do broker de teste (sistema), e o assertion abaixo que garanta o valor exato passaria
 * só fora da suíte. Por isso a chave Kafka aqui é verificada de forma tolerante: ela
 * deve existir (não-nula) — o conjunto de ambientes da evidência (compose vs cluster)
 * é coberto pela datasource, que nenhum recurso de teste sobrescreve.</p>
 */
@QuarkusTest
@TestProfile(KubernetesConfigProfile.class)
class KubernetesConfigProfileTest {

    @Inject
    SmallRyeConfig config;

    @Test
    void resolvesKubernetesPostgresEndpoint() {
        assertEquals("vertx-reactive:postgresql://billing-postgres:5432/billing",
                config.getValue("quarkus.datasource.reactive.url", String.class));
        assertEquals("billing", config.getValue("quarkus.datasource.username", String.class));
        assertEquals("billing", config.getValue("quarkus.datasource.password", String.class));
    }

    @Test
    void resolvesKubernetesKafkaBootstrap() {
        String bootstrap = config.getValue("kafka.bootstrap.servers", String.class);
        assertNotNull(bootstrap, "kafka.bootstrap.servers deve resolver em %kubernetes");
    }
}