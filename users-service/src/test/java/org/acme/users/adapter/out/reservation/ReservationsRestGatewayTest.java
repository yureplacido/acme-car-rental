package org.acme.users.adapter.out.reservation;

import io.quarkus.test.InjectMock;
import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.application.exception.AvailabilityUnavailable;
import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.port.out.ReservationsGateway;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Adapter de saida: o resultado inconclusivo da disponibilidade vira sinal de aplicacao, nunca
 * lista vazia (que o UI leria como "nenhum veiculo"). A taxonomia dos tipos lancados pelo cliente
 * esta medida contra o cliente real em {@link ReservationsClientFailureTest}.
 */
@QuarkusTest
@Timeout(30)
class ReservationsRestGatewayTest {

    private static final LocalDate START = LocalDate.of(2035, 6, 1);
    private static final LocalDate END = LocalDate.of(2035, 6, 10);

    @Inject
    ReservationsGateway gateway;

    @InjectMock
    @RestClient
    AvailabilityClient availabilityClient;

    @ConfigProperty(name = "quarkus.rest-client.reservations-availability.connect-timeout")
    long connectTimeoutMillis;

    @ConfigProperty(name = "quarkus.rest-client.reservations-availability.read-timeout")
    long readTimeoutMillis;

    /**
     * A escrita ({@code POST /reservations}, em {@link ReservationsClient}) não é idempotente
     * (restrição medida em {@code StartRentalTest}, ADR 009): um prazo curto a transformaria em
     * escrita de resultado incerto, que é justamente o modo que a ADR proíbe repetir. O prazo da
     * disponibilidade é propriedade do cliente de leitura, não do cliente inteiro.
     */
    @ConfigProperty(name = "quarkus.rest-client.reservations.read-timeout")
    Optional<Long> writeReadTimeout;

    @Test
    void shouldSignalAvailabilityUnavailableWhenReservationsAnswersAnError() {
        when(availabilityClient.availability(any(), any()))
                .thenThrow(new WebApplicationException(503));

        assertThrows(AvailabilityUnavailable.class, () -> gateway.availability(START, END));
    }

    @Test
    void shouldSignalAvailabilityUnavailableWhenTheTransportFails() {
        when(availabilityClient.availability(any(), any()))
                .thenThrow(new ProcessingException("connection refused"));

        assertThrows(AvailabilityUnavailable.class, () -> gateway.availability(START, END));
    }

    @Test
    void shouldMapTheFleetWhenReservationsResponds() {
        when(availabilityClient.availability(any(), any()))
                .thenReturn(List.of(new Car(10L, "AAA-1", "Renault", "Clio")));

        Collection<AvailableCar> cars = gateway.availability(START, END);

        assertEquals(List.of(new AvailableCar(10L, "AAA-1", "Renault", "Clio")), cars);
    }

    @Test
    void shouldBoundTheAvailabilityReadWithTransportTimeouts() {
        assertTrue(connectTimeoutMillis > 0 && connectTimeoutMillis <= 1_000,
                "connect-timeout deve ser curto e positivo, mas foi " + connectTimeoutMillis);
        assertTrue(readTimeoutMillis > 0 && readTimeoutMillis <= 3_000,
                "read-timeout deve limitar a espera do BFF, mas foi " + readTimeoutMillis);
    }

    @Test
    void shouldNotPutTheAvailabilityDeadlineOnTheNonIdempotentWrite() {
        assertTrue(writeReadTimeout.isEmpty() || writeReadTimeout.get() >= 10_000,
                "quarkus.rest-client.reservations.read-timeout deve seguir o default do Quarkus "
                        + "para a escrita não idempotente, mas foi " + writeReadTimeout);
    }
}
