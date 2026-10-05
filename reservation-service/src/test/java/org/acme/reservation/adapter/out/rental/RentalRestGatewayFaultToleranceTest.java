package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.acme.reservation.application.port.out.RentalGateway;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * Politica de fault tolerance da escrita: somente timeout.
 *
 * A operacao nao e idempotente - o rental-service sempre salva uma nova locacao em
 * {@code StartRental}, sem consultar {@code findByCustomerAndReservation} - portanto nao pode
 * haver {@code @Retry}: a segunda tentativa criaria locacao duplicada para a mesma reserva.
 * Tambem nao ha {@code @Fallback}: engolir a falha confirmaria a reserva sem locacao.
 *
 * Decisao e evidencia em docs/adr/009-fault-tolerance-chamadas-externas.md.
 */
@QuarkusTest
@Timeout(30)
class RentalRestGatewayFaultToleranceTest {

    @Inject
    RentalGateway gateway;

    @InjectMock
    @RestClient
    RentalClient client;

    @ConfigProperty(name = "quarkus.rest-client.\"org.acme.reservation.adapter.out.rental.RentalClient\".connect-timeout")
    long transportConnectTimeoutMillis;
    @ConfigProperty(name = "quarkus.rest-client.\"org.acme.reservation.adapter.out.rental.RentalClient\".read-timeout")
    long transportReadTimeoutMillis;

    @Test
    void shouldTimeOutWhenRentalServiceNeverResponds() {
        List<String> calls = recordCalls(Uni.createFrom().nothing());

        long startedAt = System.nanoTime();
        assertThrows(TimeoutException.class, () -> start());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertEquals(List.of("alice/42"), calls);
        assertTrue(elapsed.compareTo(Duration.ofMillis(1_500)) >= 0,
                "o deadline da escrita precisa ter sido aguardado: " + elapsed.toMillis() + "ms");
        assertTrue(elapsed.compareTo(Duration.ofSeconds(3)) < 0,
                "uma unica tentativa, sem retry: " + elapsed.toMillis() + "ms");
    }

    @Test
    void shouldNotRetryRentalStartWhenItFails() {
        List<String> calls = recordCalls(
                Uni.createFrom().failure(new IllegalStateException("rental is down")));

        Throwable failure = assertThrows(IllegalStateException.class, () -> start());

        assertEquals("rental is down", failure.getMessage());
        assertEquals(1, calls.size(), "sem retry cego: exatamente uma chamada ao rental");
    }

    /**
     * Caracterização da biblioteca, e a premissa da segunda camada de prazo: o {@code @Timeout}
     * avisa o chamador mas não cancela a subscription a montante. O
     * {@link Uni#onCancellation()} só dispara quando alguém cancela, então a contagem em zero
     * depois do {@code TimeoutException} é a prova.
     *
     * <p>Quando o SmallRye FT passar a cancelar, este teste falha — e a boa notícia será poder
     * remover o prazo de transporte e a guarda de configuração que o compara.
     */
    @Test
    void shouldKeepTheWriteCallInFlightWhenTheFaultToleranceDeadlineFires() {
        AtomicInteger cancellations = new AtomicInteger();
        Uni<RentalResponse> neverAnswers = Uni.createFrom().nothing();
        recordCalls(neverAnswers.onCancellation().invoke(cancellations::incrementAndGet));

        assertThrows(TimeoutException.class, () -> start());

        assertEquals(0, cancellations.get(),
                "o @Timeout avisa o chamador mas não cancela a chamada a montante");
    }

    @Test
    void shouldKeepTheWriteDeadlineAboveTheTransportTimeout() {
        assertTrue(transportReadTimeoutMillis > 0, "a escrita precisa de prazo de transporte");
        assertTrue(transportReadTimeoutMillis < RentalRestGateway.WRITE_DEADLINE_MILLIS,
                "read-timeout (" + transportReadTimeoutMillis
                        + "ms) precisa ser menor que o deadline de FT ("
                        + RentalRestGateway.WRITE_DEADLINE_MILLIS + "ms), senao a chamada fica em voo");
        assertTrue(transportConnectTimeoutMillis > 0, "a escrita precisa de prazo de connect");
        assertTrue(transportConnectTimeoutMillis < RentalRestGateway.WRITE_DEADLINE_MILLIS,
                "connect-timeout (" + transportConnectTimeoutMillis
                        + "ms) precisa ser menor que o deadline de FT ("
                        + RentalRestGateway.WRITE_DEADLINE_MILLIS + "ms), senao a fase de connect fica em voo");
    }

    @Test
    void shouldStartTheRentalWhenTheServiceAnswersInTime() {
        List<String> calls = recordCalls(Uni.createFrom().item(started()));

        start();

        assertEquals(List.of("alice/42"), calls);
    }

    private void start() {
        gateway.start("alice", 42L).await().indefinitely();
    }

    private List<String> recordCalls(Uni<RentalResponse> answer) {
        List<String> calls = new CopyOnWriteArrayList<>();
        when(client.start(anyString(), any())).thenAnswer(invocation -> {
            calls.add(invocation.getArgument(0) + "/" + invocation.getArgument(1));
            return answer;
        });
        return calls;
    }

    private static RentalResponse started() {
        return new RentalResponse("7", "alice", 42L, "2035-06-01", null, "ACTIVE");
    }
}
