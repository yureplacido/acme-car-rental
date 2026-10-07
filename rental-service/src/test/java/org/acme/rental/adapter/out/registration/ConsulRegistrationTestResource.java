package org.acme.rental.adapter.out.registration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;

/**
 * Sobe um Consul real e devolve as coordenadas dele para o adapter de registro.
 *
 * O consulo ganha o alias host.docker.internal (host-gateway) para conseguir alcancar
 * o /q/health/live do proprio JVM do teste e o check sair "passing"; a assercao de
 * registro vive em {@link RentalRegistersInConsulTest} e consulta este Consul.
 */
public class ConsulRegistrationTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> consul;

    @Override
    public Map<String, String> start() {
        consul = new GenericContainer<>("hashicorp/consul:1.20")
                .withExposedPorts(8500)
                .withExtraHost("host.docker.internal", "host-gateway");
        consul.start();
        return Map.of(
                "acme.consul.registration.consul-host", consul.getHost(),
                "acme.consul.registration.consul-port",
                String.valueOf(consul.getMappedPort(8500)));
    }

    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }
}