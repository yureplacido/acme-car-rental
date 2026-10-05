package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.RentalGateway;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
class RentalRestGatewayFaultToleranceTest {

    private static final Duration NEVER = Duration.ofSeconds(30);

    @Inject
    RentalGateway gateway;

    @InjectMock
    RentalClient client;

    @Test
    void shouldTimeOutWhenRentalServiceNeverResponds() {
        List<String> calls = recordCalls(Uni.createFrom().nothing());

        assertThrows(TimeoutException.class, () -> start());

        assertEquals(List.of("alice/42"), calls);
    }

    @Test
    void shouldNotRetryRentalStartWhenItFails() {
        List<String> calls = recordCalls(
                Uni.createFrom().failure(new IllegalStateException("rental is down")));

        Throwable failure = assertThrows(IllegalStateException.class, () -> start());

        assertEquals("rental is down", failure.getMessage());
        assertEquals(1, calls.size(), "sem retry cego: exatamente uma chamada ao rental");
    }

    @Test
    void shouldCancelTheRentalCallWhenTheTimeoutFires() {
        AtomicInteger cancellations = new AtomicInteger();
        recordCalls(Uni.createFrom().item(started())
                .onItem().delayIt().by(NEVER)
                .onCancellation().invoke(cancellations::incrementAndGet));

        assertThrows(TimeoutException.class, () -> start());

        assertEquals(1, cancellations.get(), "o timeout precisa cancelar a chamada externa");
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
