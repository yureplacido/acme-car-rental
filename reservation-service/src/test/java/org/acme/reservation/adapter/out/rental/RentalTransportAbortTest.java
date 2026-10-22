package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
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
 * Cap.8 / ADR 009 §4 — a prova do <b>abort da chamada em voo</b>.
 *
 * <p>A ADR afirma que o {@code @Timeout} do SmallRye Fault Tolerance nao cancela a chamada
 * upstream: quem aborta a requisicao HTTP e o prazo do transporte. Ate aqui isso estava apenas
 * <i>caracterizado</i> com cliente mockado
 * ({@code RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteCallInFlightWhenTheFaultToleranceDeadlineFires}),
 * e a propria ADR registrava a lacuna: nenhuma evidencia com cliente real observava o socket
 * fechando.
 *
 * <p>Este teste fecha a lacuna com um peer real: um Consul real publica a instancia "rentals"
 * apontando para um buraco negro ({@link BlackholeRentalTestResource}) que aceita a conexao,
 * le o pedido e nunca responde. O caminho exercitado e o de producao inteiro: o
 * {@code RentalClient} de producao ({@code stork://rentals}), resolvido pelo Stork no Consul,
 * e o {@link RentalGateway} de producao, com o {@code @Timeout} de 2 s.
 *
 * <p>A prova do abort nao e "a chamada falhou": e <b>o outro lado ter visto a conexao
 * fechar</b>. O buraco negro e um {@code ServerSocket} cru porque o abort chega como fim de
 * stream — o fechamento e limpo, sem RST —, e o EOF na proxima leitura e o sinal que
 * distingue "o cliente abortou" de "o cliente desistiu por timeout mas deixou a conexao
 * pendurada".
 *
 * <p>Este e o lado positivo de um experimento controlado. O lado negativo — o mesmo caminho de
 * producao, com <b>uma</b> unica variavel trocada (o prazo do transporte acima do deadline de
 * FT) — esta em {@link RentalTransportTimeoutAboveFaultToleranceDeadlineTest}. Sem ele, este teste
 * provaria que a chamada terminou, nao que a chamada foi abortada: o deadline de FT tambem
 * termina a chamada, e nada no lado do cliente distingue as duas coisas.
 *
 * <p><b>Qual fase esta provada.</b> O {@code read-timeout} do cliente REST reativo do Quarkus
 * vira {@code HttpClientRequest.setTimeout}, que e <i>inatividade</i>: ele arma um timer que
 * <b>se cancela quando os headers da resposta chegam</b>. A prova aqui cobre a fase que ele de
 * fato limita — a espera pelos headers, que no buraco negro nunca chegam. A leitura do corpo
 * <b>depois</b> dos headers nao tem prazo: e uma limitacao do {@code read-timeout} que a ADR
 * 009 §4 ja registra, e este teste nao a cobre nem a promete.
 *
 * <p><b>Margem medida.</b> O prazo do transporte (1500 ms) estoura ~450 ms antes do deadline de
 * FT (2000 ms). E essa ordem que distingue o transporte do {@code @Timeout}: se um event loop
 * carregado atrasar o transporte para alem de 2 s, o deadline de FT vence e <b>este teste
 * falha</b> — com mensagem explicita, nunca em silencio.
 */
@QuarkusTest
@Tag("consul")
@Timeout(30)
@QuarkusTestResource(value = BlackholeRentalTestResource.class, restrictToAnnotatedClass = true)
class RentalTransportAbortTest {

    @Inject
    RentalGateway gateway;

    @Inject
    @ConfigProperty(name = "quarkus.rest-client.\"org.acme.reservation.adapter.out.rental.RentalClient\".read-timeout")
    long transportReadTimeoutMillis;

    @Test
    void shouldAbortTheWriteInFlightWhenTheTransportTimeoutFires() throws InterruptedException {
        long startedAt = System.nanoTime();
        // A guarda de que a chamada nao teve sucesso e uma assercao, e nao um "fail" engolido
        // por um catch: um peer que nunca responde nao pode devolver sucesso. E o tipo e
        // Exception (e nao Throwable) para que um Error incidental nao passe como a falha
        // esperada.
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

            // 1. a falha nao veio do deadline de fault tolerance: o prazo do transporte e menor e
            //    estourou antes. Um TimeoutException de FT aqui seria o deadline de 2 s vencendo.
            assertFalse(hasCause(thrown,
                            org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException.class),
                    "a falha veio do deadline de fault tolerance, nao do prazo do transporte: " + describe(thrown));

            // 2. a causa e um estouro de prazo do transporte (o NoStackTraceTimeoutException do
            //    Vert.x estende java.util.concurrent.TimeoutException).
            assertTrue(hasCause(thrown, java.util.concurrent.TimeoutException.class),
                    "a falha nao e um estouro de prazo: " + describe(thrown));

            // 3. coerente com o prazo do transporte, e estritamente antes do deadline de FT.
            assertTrue(elapsedMillis < RentalRestGateway.WRITE_DEADLINE_MILLIS,
                    "a chamada falhou em " + elapsedMillis + " ms, depois do deadline de FT ("
                            + RentalRestGateway.WRITE_DEADLINE_MILLIS + " ms): " + describe(thrown));
            assertTrue(elapsedMillis >= transportReadTimeoutMillis - 100,
                    "a chamada falhou em " + elapsedMillis + " ms, antes do prazo do transporte ("
                            + transportReadTimeoutMillis + " ms)");

            // 4. o abort saiu da maquina: o outro lado viu a conexao fechar.
            assertTrue(connection.awaitClientClose(5, TimeUnit.SECONDS),
                    "o buraco negro nunca viu o cliente fechar a conexao: " + connection.outcome());

            // 5. e o fechamento foi limpo (fim de stream), e nao um reset — que e o mecanismo
            //    que a ADR 009 §4 descreve.
            assertTrue(connection.closedCleanly(),
                    "o fechamento nao foi limpo (fim de stream): " + connection.outcome());
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
