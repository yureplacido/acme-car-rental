package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.junit.TestProfile;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.RentalGateway;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Cap.8 / ADR 009 §4 — o lado <b>negativo</b> do experimento do abort em voo.
 *
 * <p>{@link RentalTransportAbortTest} prova que, com o prazo do transporte <b>abaixo</b> do
 * deadline de fault tolerance, e o transporte que corta a chamada em voo: o peer ve o socket
 * fechar, e a causa e um estouro de prazo do transporte.
 *
 * <p>Este teste fecha a <b>causalidade</b> com uma unica variavel trocada: o mesmo caminho de
 * producao inteiro (Consul real → Stork → {@code RentalClient} → {@link RentalGateway} com o
 * {@code @Timeout} de 2 s), o mesmo buraco negro, e o prazo do transporte <b>acima</b> do
 * deadline de FT ({@link TransportTimeoutAboveDeadlineProfile}). A ordem se inverte, e o
 * resultado e o oposto:
 *
 * <ul>
 *   <li>o deadline de FT vence primeiro e entrega {@code TimeoutException} ao chamador;</li>
 *   <li>o prazo do transporte nao dispara — a falha nao e um estouro de prazo do transporte;</li>
 *   <li>e o socket <b>continua aberto</b> do lado do peer: a chamada foi <b>abandonada</b>,
 *       nao abortada. O {@code @Timeout} do SmallRye Fault Tolerance nao cancela a subscription
 *       a montante.</li>
 * </ul>
 *
 * <p>Sem este teste, o anterior provaria que a chamada terminou, nao que a chamada foi
 * abortada — o deadline de FT tambem termina a chamada, e nada no lado do cliente distingue as
 * duas coisas. E, como a mutacao esta versionada (e nao em um experimento manual, revertido
 * depois), a forca do teste do caminho de producao e verificada pelo build a cada execucao.
 */
@QuarkusTest
@Tag("consul")
@Timeout(30)
@TestProfile(TransportTimeoutAboveDeadlineProfile.class)
@QuarkusTestResource(value = BlackholeRentalTestResource.class, restrictToAnnotatedClass = true)
class RentalTransportTimeoutAboveFaultToleranceDeadlineTest {

    @Inject
    RentalGateway gateway;

    @Inject
    @ConfigProperty(name = "quarkus.rest-client.\"org.acme.reservation.adapter.out.rental.RentalClient\".read-timeout")
    long transportReadTimeoutMillis;

    @Test
    void shouldAbandonTheWriteInFlightWhenTheTransportTimeoutIsAboveTheFaultToleranceDeadline()
            throws InterruptedException {
        // Se a mutacao nao tivesse sido aplicada, este teste estaria provando a mesma coisa do
        // outro — e o par nao seria um experimento controlado.
        assertTrue(transportReadTimeoutMillis > RentalRestGateway.WRITE_DEADLINE_MILLIS,
                "a mutacao deste teste nao foi aplicada: o prazo do transporte ("
                        + transportReadTimeoutMillis + " ms) precisa ficar ACIMA do deadline de FT ("
                        + RentalRestGateway.WRITE_DEADLINE_MILLIS + " ms)");

        long startedAt = System.nanoTime();
        Throwable thrown = assertThrows(Exception.class,
                () -> gateway.start("alice", 42L).await().indefinitely(),
                "a chamada nao deveria ter sucesso contra um peer que nunca responde");
        long elapsedMillis = (System.nanoTime() - startedAt) / 1_000_000;

        BlackholeRentalTestResource.BlackholeConnection connection =
                BlackholeRentalTestResource.CONNECTIONS.poll(5, TimeUnit.SECONDS);
        assertNotNull(connection, "nenhuma conexao chegou ao buraco negro");
        try {
            assertTrue(connection.awaitRequest(5, TimeUnit.SECONDS),
                    "o pedido nao chegou ao buraco negro: " + connection.outcome());

            // 0. o pedido saiu de verdade, pelo caminho de producao (Stork -> Consul -> peer).
            assertTrue(connection.requestHead().contains("POST /rental/start/alice/42"),
                    "o peer nao recebeu o pedido de producao: " + connection.requestHead());

            // 1. a falha veio do deadline de fault tolerance...
            assertTrue(hasCause(thrown,
                            org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class),
                    "a falha nao veio do deadline de fault tolerance: " + describe(thrown));

            // 2. ...e nao do prazo do transporte. As duas hierarquias sao disjuntas: a de FT nao
            //    estende java.util.concurrent.TimeoutException, que e a do Vert.x.
            assertFalse(hasCause(thrown, java.util.concurrent.TimeoutException.class),
                    "o prazo do transporte disparou, o que nao deveria acontecer com a mutacao deste "
                            + "teste: " + describe(thrown));

            // 3. o deadline de FT venceu (2 s) e o prazo do transporte nao (5 s).
            assertTrue(elapsedMillis >= RentalRestGateway.WRITE_DEADLINE_MILLIS - 100,
                    "a chamada falhou em " + elapsedMillis + " ms, antes do deadline de FT ("
                            + RentalRestGateway.WRITE_DEADLINE_MILLIS + " ms): " + describe(thrown));
            assertTrue(elapsedMillis < transportReadTimeoutMillis,
                    "a chamada falhou em " + elapsedMillis + " ms, depois do prazo do transporte ("
                            + transportReadTimeoutMillis + " ms): " + describe(thrown));

            // 4. e a chamada NAO foi abortada: o socket continua aberto do lado do peer. E o
            //    deadline de FT que devolveu a falha ao chamador — a chamada ficou orfa.
            long windowAfterDeadline = Math.max(0, RentalRestGateway.WRITE_DEADLINE_MILLIS - elapsedMillis)
                    + 1_000;
            assertFalse(connection.awaitClientClose(windowAfterDeadline, TimeUnit.MILLISECONDS),
                    "o socket foi fechado pelo cliente, mas o deadline de FT nao aborta a chamada: "
                            + connection.outcome());
        } finally {
            connection.close();
        }
    }

    private static boolean hasCause(Throwable thrown, Class<? extends Throwable> type) {
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            if (type.isInstance(current)) {
                return true;
            }
            if (current.getCause() == current) {
                return false;
            }
        }
        return false;
    }

    private static String describe(Throwable thrown) {
        if (thrown == null) {
            return "sem excecao";
        }
        StringBuilder description = new StringBuilder();
        for (Throwable current = thrown; current != null; current = current.getCause()) {
            description.append(current.getClass().getName()).append(": ").append(current.getMessage())
                    .append(" | ");
            if (current.getCause() == current) {
                break;
            }
        }
        return description.toString();
    }
}
