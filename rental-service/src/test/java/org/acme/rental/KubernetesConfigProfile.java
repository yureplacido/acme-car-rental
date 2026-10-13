package org.acme.rental;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Cap.11 item 11.1 - ativa o config profile {@code kubernetes} no teste (mesmo
 * recurso dos demais serviços). Overrides apenas de seguranca de boot: porta de
 * teste aleatoria e sem Dev Services de Mongo de graca (o connection-string do
 * provider vem da config, nao do container).
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
                "quarkus.mongodb.devservices.enabled", "false");
    }
}