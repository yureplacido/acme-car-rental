package org.acme.rental.adapter.out.registration;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Evidencia executavel do contrato "Consul fora nao derruba o boot nem trava o shutdown"
 * (docs/testing.md secao service discovery; ADR 010): o registro no StartupEvent falha
 * (porta 1, inalcancavel), o app continua subindo - prova de que o teste roda e que o
 * bean foi criado - e o register() nao estoura nada (a falha e contida e apenas logada).
 * A assinatura do deregister() continua o erro tipado (IOException), que o @PreDestroy
 * captura - e por isso a chamada de baixo nivel aparece com throws esperado aqui.
 */
@QuarkusTest
@Tag("consul")
@TestProfile(ConsulRegistrationFailureProfile.class)
class ConsulRegistrationFailureTest {

    @Inject
    ConsulServiceRegistration registration;

    @Test
    void shouldContinueBootAndContainFailureWhenConsulIsUnreachable() {
        assertNotNull(registration, "o app subiu mesmo com o registro falhando no boot");

        assertDoesNotThrow(() -> registration.register(),
                "register() contem a falha de conectividade e so loga");

        assertThrows(IOException.class, registration::deregister,
                "deregister() sobe o erro tipado, que o @PreDestroy captura sem derrubar o shutdown");
    }
}