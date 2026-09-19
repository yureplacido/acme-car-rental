package org.acme.reservation;

import io.quarkus.test.junit.QuarkusIntegrationTest;

/**
 * Livro 5.2.2: teste de integração nativo.
 *
 * <p>Herda todos os testes de {@link ReservationResourceTest} e roda contra o artefato
 * pré-compilado (JAR, container ou binário nativo via {@code ./mvnw verify -Dnative}).
 * Assim como no livro, o nome usa o sufixo <em>IT</em> para que o surefire (JVM) o ignore
 * e o failsafe o execute apenas quando solicitado ({@code skipITs=true} no pom). O teste
 * que depende de mock será pulado no nativo por @DisabledOnIntegrationTest.</p>
 */
@QuarkusIntegrationTest
public class ReservationResourceIT extends ReservationResourceTest {
}