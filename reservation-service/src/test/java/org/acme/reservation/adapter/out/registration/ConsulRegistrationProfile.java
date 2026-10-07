package org.acme.reservation.adapter.out.registration;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Liga o registro automatico do concessionario so nesta classe.
 *
 * O application.properties desliga em %test para as demais suites nao tentarem se
 * registrar sem Consul por perto; aqui ele volta e a porta de registro fica fixa,
 * para a assercao em ConsultaCatalogo ser deterministica (o recorder usaria
 * quarkus.http.port se nao estivesse explicita).
 */
public class ConsulRegistrationProfile implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.stork.reservations.service-registrar.enabled", "true",
                "quarkus.stork.reservations.service-registrar.port", "18091");
    }
}