package org.acme.reservation.repository.memory;

import io.quarkus.arc.properties.IfBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.reservation.model.Reservation;
import org.acme.reservation.repository.ReservationsRepository;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;


@ApplicationScoped
@IfBuildProperty(name = "app.repository", stringValue = "memory", enableIfMissing = true)
public class InMemoryReservationRepository implements ReservationsRepository {

    private final AtomicLong idGenerator = new AtomicLong(3);

    // Lista mutável simulando a tabela do banco de dados em memória
    private final List<Reservation> reservations = new ArrayList<>(List.of(
            Reservation.builder()
                    .id(1L)
                    .carId(1L) // Vinculado ao Mazda da aula anterior
                    .startDay(LocalDate.now())
                    .endDay(LocalDate.now().plusDays(3))
                    .build(),
            Reservation.builder()
                    .id(2L)
                    .carId(2L) // Vinculado ao Ford Mustang
                    .startDay(LocalDate.now().plusDays(5))
                    .endDay(LocalDate.now().plusDays(10))
                    .build(),
            Reservation.builder()
                    .id(3L)
                    .carId(1L)
                    .startDay(LocalDate.now().plusDays(15))
                    .endDay(LocalDate.now().plusDays(20))
                    .build()
    ));


    public List<Reservation> findAll() {
        return reservations;
    }

    public Reservation save(Reservation reservation) {
        if (reservation.getId() == null) {
            reservation.setId(idGenerator.incrementAndGet());
        }
        reservations.add(reservation);
        return reservation;
    }
}
