package org.acme.reservation.adapter.out.registration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Liga o registro proprio neste teste e aponta o endereco registrado para
 * host.docker.internal, que o Consul (testcontainers, com host-gateway) alcanca, para o
 * health check HTTP do /q/health/live sair "passing".
 *
 * O application.properties desliga em %test; aqui ele volta. A porta precisa ser fixa
 * nesta classe: com %test...test-port=0 (aleatoria) o valor de config nao reflete o
 * listener real no StartupEvent e o Consul omitiria "Port" do catalogo (omitempty).
 * Cada modulo de teste de registro usa a sua porta desta familia 18xxx (reservation
 * 18081, rental 18082), para dois builds paralelos no mesmo host nao colidirem.
 */
public class ConsulRegistrationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "acme.consul.registration.enabled", "true",
                "acme.consul.registration.address", "host.docker.internal",
                "quarkus.http.test-port", "18081");
    }
}