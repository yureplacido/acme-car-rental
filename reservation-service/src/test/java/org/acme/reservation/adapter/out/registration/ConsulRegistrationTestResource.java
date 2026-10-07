package org.acme.reservation.adapter.out.registration;

import io.quarkus.test.common.QuarkusTestResourceLifecycleManager;
import org.testcontainers.containers.GenericContainer;

import java.util.Map;

/**
 * Sobe um Consul real e devolve as coordenadas dele para o registrar do Stork.
 *
 * O reservation-service se registra no boot; a assercao de registro vive em
 * {@link ReservationRegistersInConsulTest} e consulta este mesmo Consul.
 */
public class ConsulRegistrationTestResource implements QuarkusTestResourceLifecycleManager {

    private GenericContainer<?> consul;

    @Override
    public Map<String, String> start() {
        consul = new GenericContainer<>("hashicorp/consul:1.20").withExposedPorts(8500);
        consul.start();
        return Map.of(
                "quarkus.stork.reservations.service-registrar.consul-host", consul.getHost(),
                "quarkus.stork.reservations.service-registrar.consul-port",
                String.valueOf(consul.getMappedPort(8500)));
    }

    @Override
    public void stop() {
        if (consul != null) {
            consul.stop();
        }
    }
}