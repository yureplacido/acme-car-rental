package org.acme.reservation;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;
import java.util.Set;

/**
 * Livro 5.4: definição de um perfil de teste (implementa {@link QuarkusTestProfile}).
 *
 * <p>Analogia simples: assim como um aplicativo roda com perfis dev/test/prod, os TESTES
 * também podem rodar com "perfis" próprios. O perfil carrega um {@code application.properties}
 * ALTERNATIVO por cima do padrão — sem alterar o arquivo original nem o código.</p>
 *
 * <p>O que cada método faz:</p>
 * <ul>
 *   <li>{@code getConfigOverrides()} — mapa {@code chave=valor} que SOBRESCREVE as
 *       propriedades do {@code application.properties} durante os testes que ativarem
 *       este perfil. Ex.: apontar o client GraphQL do Inventory para um servidor de
 *       staging em vez de localhost.</li>
 *   <li>{@code tags()} — rótulos opcionais (podem ser vários) para selecionar PERFIS
 *       numa execução com {@code -Dquarkus.test.profile.tags=...}: só os testes cujo
 *       perfil tenha alguma das tags são executados (os demais são pulados).</li>
 * </ul>
 */
public class RunWithStaging implements QuarkusTestProfile {

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of("quarkus.smallrye-graphql-client.inventory.url", "http://staging.service.com/graphql");
    }

    @Override
    public Set<String> tags() {
        return Set.of("staging");
    }
}