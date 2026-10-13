package org.acme.reservation;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Cap.11 item 11.1 - ativa o config profile {@code kubernetes} no teste, no mesmo
 * formato que {@link RunWithStaging} usa para o perfil de teste.
 *
 * <p>{@code getConfigProfile()} troca o PROFILE ATIVO do Quarkus config system para
 * {@code kubernetes}: os prefixos {@code %kubernetes.*} passam a valer e os
 * {@code %test.*} deixam de valer. Os overrides abaixo são apenas de SEGURANÇA DE
 * BOOT - não mascaram nenhuma chave de provider a ser verificada:</p>
 * <ul>
 *   <li>{@code quarkus.http.test-port=0} - o {@code %test.quarkus.http.test-port}
 *       não vale mais sob o perfil kubernetes (porta 8081 seria disputada).</li>
 *   <li>{@code quarkus.hibernate-orm.database.generation=none} - sem DDL no boot;
 *       o {@code drop-and-create} global tentaria conectar num host de cluster que
 *       não existe na JVM do teste.</li>
 *   <li>{@code quarkus.oidc.tenant-enabled=false} - sem descoberta do auth-server
 *       no boot; a chave {@code quarkus.oidc.auth-server-url} (o provider em si)
 *       continua resolvível e é o que o teste assere.</li>
 *   <li>{@code quarkus.kubernetes-client.devservices.enabled=false} - já é falso no
 *       {@code application.properties}; repetido por robustez contra mudança futura.</li>
 * </ul>
 * <p>O que o teste prova: a MESMA imagem (este jar) resolve os providers do cluster
 * sem rebuild — só pelo {@code QUARKUS_PROFILE}. O par dessa evidência é o smoke do
 * compose (ADR 011): lá, a mesma imagem sobe com {@code %docker} (Consul/localhost).</p>
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
                "quarkus.hibernate-orm.database.generation", "none",
                "quarkus.oidc.tenant-enabled", "false",
                "quarkus.kubernetes-client.devservices.enabled", "false");
    }
}