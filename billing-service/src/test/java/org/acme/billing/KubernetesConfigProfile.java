package org.acme.billing;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Cap.11 item 11.1 - ativa o config profile {@code kubernetes} no teste (mesmo
 * recurso que {@code reservation-service.KubernetesConfigProfile}).
 *
 * <p>Os overrides sao apenas de seguranca de boot, nenhum mascara chave de provider:</p>
 * <ul>
 *   <li>{@code quarkus.http.test-port=0} - o {@code %test.*} nao vale sob kubernetes.</li>
 *   <li>{@code quarkus.hibernate-orm.database.generation=none} - sem DDL contra o
 *       Postgres do cluster (que nao existe na JVM do teste).</li>
 *   <li>{@code quarkus.kafka.devservices.enabled=false} - nao subir broker Dev Service
 *       "de graca" sob o perfil kubernetes (o bootstrap vem da config do provider).</li>
 * </ul>
 */
public class KubernetesConfigProfile implements QuarkusTestProfile {

    @Override
    public String getConfigProfile() {
        return "kubernetes";
    }

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.http.test-port", "0",
                "quarkus.hibernate-orm.database.generation", "none",
                "quarkus.kafka.devservices.enabled", "false");
    }
}