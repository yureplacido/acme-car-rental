package org.acme.users;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Cap.11 item 11.1 - ativa o config profile {@code kubernetes} no teste (mesmo
 * recurso dos demais servicos). Overrides apenas de seguranca de boot: porta de
 * teste aleatoria e sem tenant OIDC de graca (a URL do provider vem da config).
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
                "quarkus.oidc.tenant-enabled", "false",
                "quarkus.kubernetes-client.devservices.enabled", "false");
    }
}