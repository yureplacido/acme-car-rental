package org.acme.reservation;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

/**
 * Livro 5.4: demonstra um @TestProfile ativo ({@link RunWithStaging}).
 *
 * <p>Perfil de teste (QuarkusTestProfile) é como um "ambiente" para os testes: em vez de
 * dev/preprod/prod, escolhemos configurações válidas só para o teste. Ele permite rodar a
 * MESMA aplicação sob configurações DIFERENTES sem tocar no {@code application.properties}.</p>
 *
 * <p>Pipeline de configuração: o Quarkus lê o {@code application.properties} padrão e depois
 * aplica por cima as sobrescrições ({@code getConfigOverrides}) do perfil ativo. O
 * {@code @TestProfile} liga o perfil apenas a ESTA classe de teste — as demais classes
 * seguem usando as configurações normais.</p>
 *
 * <p>Aqui validamos que a sobrescrita realmente valeu: injetamos a propriedade (via
 * {@code @ConfigProperty}) e esperamos o valor de staging em vez do localhost padrão.</p>
 */
@QuarkusTest
@TestProfile(RunWithStaging.class)
public class StagingTest {

    @ConfigProperty(name = "quarkus.smallrye-graphql-client.inventory.url")
    String inventoryUrl;

    @Test
    public void testStagingProfileOverridesGraphQLUrl() {
        Assertions.assertEquals("http://staging.service.com/graphql", inventoryUrl);
    }
}