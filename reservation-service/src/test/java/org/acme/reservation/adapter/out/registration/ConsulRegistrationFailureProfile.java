package org.acme.reservation.adapter.out.registration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Liga o registro proprio apontando para um Consul inalcancavel (porta 1). So existe
 * para o teste de falha verificar que o boot segue e que o adapter contem a falha.
 */
public class ConsulRegistrationFailureProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "acme.consul.registration.enabled", "true",
                "acme.consul.registration.consul-host", "127.0.0.1",
                "acme.consul.registration.consul-port", "1");
    }
}