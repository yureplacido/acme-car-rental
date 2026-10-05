package org.acme.reservation.adapter.out.inventory;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.ServerErrorException;
import org.acme.reservation.adapter.out.inventory.model.Car;
import org.acme.reservation.application.exception.InventoryUnavailable;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.query.AvailableVehicle;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

/**
 * Politica de fault tolerance da leitura de disponibilidade: timeout, retry so para falha
 * transitoria e fallback que sinaliza indisponibilidade em vez de devolver lista vazia -
 * "nenhum veiculo disponivel" seria resposta falsa com o inventory fora do ar.
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
        AtomicInteger attempts = restarting(2);

        List<AvailableVehicle> vehicles = findVehicles();

        assertEquals(3, attempts.get(), "uma falha transitoria pode ser repetida");
        assertEquals(List.of(
                new AvailableVehicle(10L, "AAA-1", "Renault", "Clio"),
                new AvailableVehicle(20L, "BBB-2", "Fiat", "Panda")), vehicles);
    }

    @Test
    void shouldNotRetryWhenInventoryRejectsTheQuery() {
        AtomicInteger attempts = answering(Uni.createFrom().failure(
                new BadRequestException("unknown field allCars")));

        Throwable failure = assertThrows(BadRequestException.class, () -> findVehicles());

        assertEquals("unknown field allCars", failure.getMessage());
        assertEquals(1, attempts.get(), "falha deterministica nao pode ser repetida");
    }

    @Test
    void shouldFailWithInventoryUnavailableWhenTheReadKeepsTimingOut() {
        AtomicInteger attempts = answering(Uni.createFrom().nothing());

        long startedAt = System.nanoTime();
        Throwable failure = assertThrows(InventoryUnavailable.class, () -> findVehicles());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertInstanceOf(TimeoutException.class, rootCause(failure));
        assertEquals(3, attempts.get(), "uma tentativa e duas repeticoes, e nada mais");
        assertTrue(elapsed.compareTo(Duration.ofSeconds(4)) < 0,
                "o deadline do %test deve valer: " + elapsed.toMillis() + "ms");
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

    /** Inventory reiniciando: 5xx, falha de transporte que vale outra tentativa. */
    private AtomicInteger restarting(int failuresBeforeSuccess) {
        AtomicInteger attempts = new AtomicInteger();
        when(client.allCars()).thenAnswer(invocation -> attempts.getAndIncrement() < failuresBeforeSuccess
                ? Uni.createFrom().failure(new ServerErrorException("inventory is restarting", 503))
                : twoCars());
        return attempts;
    }

    /** Inventory sempre responde a mesma coisa, tentativa por tentativa. */
    private AtomicInteger answering(Uni<List<Car>> response) {
        AtomicInteger attempts = new AtomicInteger();
        when(client.allCars()).thenAnswer(invocation -> {
            attempts.incrementAndGet();
            return response;
        });
        return attempts;
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
}
