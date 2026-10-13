package org.acme.inventory;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Cap.11 item 11.1 - ativa o config profile {@code kubernetes} no teste (mesmo
 * recurso dos demais serviços). Overrides apenas de segurança de boot:
 * porta de teste aleatória, sem DDL no boot e sem broker Dev Service de graça.
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