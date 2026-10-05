package org.acme.reservation.adapter.out.inventory;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import org.acme.reservation.adapter.out.inventory.model.Car;
import org.acme.reservation.application.exception.InventoryUnavailable;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.query.AvailableVehicle;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Politica de fault tolerance da leitura: timeout, retry so para falha transitoria e fallback
 * que sinaliza indisponibilidade em vez de devolver lista vazia - "nenhum veiculo disponivel"
 * seria uma resposta falsa quando o inventory esta fora.
 *
 * Decisao e evidencia em docs/adr/009-fault-tolerance-chamadas-externas.md.
 */
@QuarkusTest
class GraphQLInventoryGatewayFaultToleranceTest {

    @Inject
    InventoryGateway gateway;

    @InjectMock
    GraphQLInventoryClient client;

    @Test
    void shouldRetryTransientInventoryFailureAndThenReturnVehicles() {
        AtomicInteger attempts = answering(failing(2, new IllegalStateException("inventory is restarting")), twoCars());

        List<AvailableVehicle> vehicles = findVehicles();

        assertEquals(3, attempts.get(), "uma falha transitoria pode ser repetida");
        assertEquals(List.of(
                new AvailableVehicle(10L, "AAA-1", "Renault", "Clio"),
                new AvailableVehicle(20L, "BBB-2", "Fiat", "Panda")), vehicles);
    }

    @Test
    void shouldNotRetryWhenInventoryRejectsTheQuery() {
        AtomicInteger attempts = answering(Uni.createFrom().failure(new BadRequestException("unknown field allCars")));

        Throwable failure = assertThrows(BadRequestException.class, () -> findVehicles());

        assertEquals("unknown field allCars", failure.getMessage());
        assertEquals(1, attempts.get(), "falha deterministica nao pode ser repetida");
    }

    @Test
    void shouldFailWithInventoryUnavailableWhenTheReadKeepsTimingOut() {
        AtomicInteger attempts = answering(Uni.createFrom().nothing());

        Throwable failure = assertThrows(InventoryUnavailable.class, () -> findVehicles());

        assertInstanceOf(TimeoutException.class, rootCause(failure));
        assertTrue(attempts.get() > 1, "o retry deve ter tentado antes do fallback");
    }

    @Test
    void shouldReturnTheVehiclesWhenInventoryAnswersInTime() {
        answering(twoCars());

        List<AvailableVehicle> vehicles = findVehicles();

        assertEquals(2, vehicles.size());
        assertEquals(10L, vehicles.get(0).id());
        assertEquals("AAA-1", vehicles.get(0).licensePlateNumber());
        assertEquals("Renault", vehicles.get(0).manufacturer());
        assertEquals("Clio", vehicles.get(0).model());
        assertEquals(20L, vehicles.get(1).id());
    }

    private List<AvailableVehicle> findVehicles() {
        return gateway.findVehicles().await().indefinitely();
    }

    /**
     * Programa as respostas do client em ordem: cada assinatura vale para uma tentativa; a ultima
     * se repete. Devolve o contador de tentativas.
     */
    private AtomicInteger answering(Uni<List<Car>> first, Uni<List<Car>>... then) {
        AtomicInteger attempts = new AtomicInteger();
        List<Uni<List<Car>>> responses = Stream.concat(Stream.of(first), Stream.of(then)).toList();
        when(client.allCars()).thenAnswer(invocation -> {
            int attempt = attempts.getAndIncrement();
            return responses.get(Math.min(attempt, responses.size() - 1));
        });
        return attempts;
    }

    /**
     * Falha as N primeiras tentativas e so entao responde.
     */
    private static Uni<List<Car>> failing(int failures, Throwable failure) {
        return Uni.createFrom().deferred(() -> new FailingRead(failures, failure).next());
    }

    private static Uni<List<Car>> twoCars() {
        return Uni.createFrom().item(List.of(
                new Car(10L, "AAA-1", "Renault", "Clio"),
                new Car(20L, "BBB-2", "Fiat", "Panda")));
    }

    private static Throwable rootCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class FailingRead {

        private final AtomicInteger remainingFailures;
        private final Throwable failure;

        private FailingRead(int failures, Throwable failure) {
            this.remainingFailures = new AtomicInteger(failures);
            this.failure = failure;
        }

        private Uni<List<Car>> next() {
            return remainingFailures.getAndDecrement() > 0
                    ? Uni.createFrom().failure(failure)
                    : twoCars();
        }
    }
}
