package org.acme.reservation;

import io.quarkus.test.junit.QuarkusTest;
import jakarta.inject.Inject;
import org.acme.reservation.model.Reservation;
import org.acme.reservation.repository.ReservationsRepository;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Livro 5.1.1: teste white-box do repositório de reservas.
 *
 * <p>O que é: um teste que enxerga "por dentro". Em vez de entrar pela porta da frente
 * (HTTP/REST), ele conversa direto com o CDI e injeta o bean {@link ReservationsRepository}.</p>
 *
 * <p>Por que o repositório in-memory: a implementação concreta é escolhida por configuração
 * ({@code app.repository=memory}, via {@code @IfBuildProperty} no InMemoryReservationsRepository),
 * então o teste não precisa de banco de dados — o cap.7 ainda vai plugar a persistência real.</p>
 *
 * <p>O que o teste prova: {@code save()} atribui um id à reserva e ela passa a constar em
 * {@code findAll()}. Como o repositório in-memory não valida se o carro existe, qualquer
 * {@code carId} serve (384L é arbitrário).</p>
 *
 * <p>Atenção: este teste só roda em JVM — a injeção de CDI no próprio teste não existe no
 * nativo (por isso o teste REST {@link ReservationResourceTest} tem um par nativo,
 * {@link ReservationResourceIT}).</p>
 */
@QuarkusTest
public class ReservationRepositoryTest {

    @Inject
    ReservationsRepository repository;

    @Test
    public void testCreateReservation() {
        // White-box: fala direto com o repositório (CDI), sem passar pelo REST.
        // Como o repositório in-memory não valida existência do carro, qualquer
        // carId serve (384L é arbitrário). Datas futuras relativas a "hoje".
        Reservation reservation = new Reservation();
        reservation.setStartDay(LocalDate.now().plus(5, ChronoUnit.DAYS));
        reservation.setEndDay(LocalDate.now().plus(12, ChronoUnit.DAYS));
        reservation.setCarId(384L);

        repository.save(reservation);

        Assertions.assertNotNull(reservation.getId());
        Assertions.assertTrue(repository.findAll().contains(reservation));
    }
}