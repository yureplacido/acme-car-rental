package org.acme.reservation.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Modelo de domínio da reserva — POJO puro. É o tipo público do REST
 * ({@code /reservations}); a persistência vive em {@code entity/ReservationEntity}
 * (Panache reativo), mapeada por {@code ReservationMapper}. Assim o Active Record
 * ou o Repository pattern podem mudar sem tocar no modelo/API.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Reservation {

    private Long id;
    private Long carId;
    private LocalDate startDay;
    private LocalDate endDay;
    private String userId;

    /**
     * Verifica se o período informado sobrepõe esta reserva (mesma regra do livro 5.1).
     */
    public boolean isReserved(LocalDate startDay, LocalDate endDay) {
        return !(this.endDay.isBefore(startDay) || this.startDay.isAfter(endDay));
    }
}