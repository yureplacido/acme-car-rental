package org.acme.reservation.adapter.out.inventory;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import io.smallrye.graphql.client.GraphQLClientException;
import io.smallrye.graphql.client.InvalidResponseException;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.acme.reservation.adapter.out.inventory.model.Car;
import org.acme.reservation.application.exception.InventoryUnavailable;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.query.AvailableVehicle;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.net.ConnectException;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
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
 * <p>As falhas injetadas aqui sao as mesmas que o cliente real produz, medidas em
 * {@link GraphQLInventoryClientFailureTest}: sem isso a policy testaria tipos que a biblioteca
 * nunca lança.
 *
 * <p>Decisao e evidencia em docs/adr/009-fault-tolerance-chamadas-externas.md.
 */
@QuarkusTest
@Timeout(30)
class GraphQLInventoryGatewayFaultToleranceTest {

    @Inject
    InventoryGateway gateway;

    @InjectMock
    GraphQLInventoryClient client;

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

    @Test
    void shouldRetryWhenInventoryAnswersWithoutGraphqlEnvelope() {
        AtomicInteger attempts = failingTwiceThenAnswering(
                new InvalidResponseException("Unexpected response. Code=503, message=\"Service Unavailable\""));

        List<AvailableVehicle> vehicles = findVehicles();

        assertEquals(3, attempts.get(), "uma falha transitoria pode ser repetida");
        assertEquals(2, vehicles.size());
    }

    @Test
    void shouldRetryWhenTheConnectionToInventoryFails() {
        AtomicInteger attempts = failingTwiceThenAnswering(
                new CompletionException(new ConnectException("connection refused")));

        List<AvailableVehicle> vehicles = findVehicles();

        assertEquals(3, attempts.get(), "o embrulho do client tem de ser desfeito para o retry valer");
        assertEquals(2, vehicles.size());
    }

    @Test
    void shouldFailWithInventoryUnavailableWhenTheReadKeepsTimingOut() {
        AtomicInteger attempts = answering(Uni.createFrom().nothing());

        long startedAt = System.nanoTime();
        Throwable failure = assertThrows(InventoryUnavailable.class, () -> findVehicles());
        Duration elapsed = Duration.ofNanos(System.nanoTime() - startedAt);

        assertInstanceOf(TimeoutException.class, rootCause(failure));
        assertEquals(3, attempts.get(), "uma tentativa e duas repeticoes, e nada mais");
        assertTrue(elapsed.compareTo(Duration.ofMillis(300)) >= 0,
                "o deadline do %test precisa ter sido aguardado: " + elapsed.toMillis() + "ms");
        assertTrue(elapsed.compareTo(Duration.ofSeconds(2)) < 0,
                "tres tentativas de 300ms nao podem passar de 2s: " + elapsed.toMillis() + "ms");
    }

    @Test
    void shouldFailWithInventoryUnavailableWhenInventoryKeepsAnsweringWithoutGraphqlEnvelope() {
        AtomicInteger attempts = answering(Uni.createFrom().failure(
                new InvalidResponseException("Unexpected response. Code=503, message=\"Service Unavailable\"")));

        Throwable failure = assertThrows(InventoryUnavailable.class, () -> findVehicles());

        assertInstanceOf(InvalidResponseException.class, rootCause(failure));
        assertEquals(3, attempts.get());
    }

    @Test
    void shouldNotRetryWhenInventoryAnswersWithGraphqlErrors() {
        AtomicInteger attempts = answering(
                Uni.createFrom().failure(new GraphQLClientException("schema exploded", List.of())));

        Throwable failure = assertThrows(GraphQLClientException.class, () -> findVehicles());

        assertEquals("schema exploded", failure.getMessage());
        assertEquals(1, attempts.get(), "erro de GraphQL e defeito do outro lado, nao indisponibilidade");
    }

    private List<AvailableVehicle> findVehicles() {
        return gateway.findVehicles().await().indefinitely();
    }

    /** Inventory reiniciando: falha transitoria que pode virar outra tentativa. */
    private AtomicInteger failingTwiceThenAnswering(Throwable transientFailure) {
        AtomicInteger attempts = new AtomicInteger();
        when(client.allCars()).thenAnswer(invocation -> attempts.getAndIncrement() < 2
                ? Uni.createFrom().failure(transientFailure)
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
        while (current.getCause() != null && current.getCause() != current) {
            current = current.getCause();
        }
        return current;
    }
}
