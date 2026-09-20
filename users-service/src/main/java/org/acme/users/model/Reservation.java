package org.acme.users.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDate;

/**
 * Cópia simplificada do Reservation do reservation-service (livro 6.7),
 * usada pelo users-service para listar e criar reservas do usuário.
 */

@Data
@Builder
public class Reservation {
    private Long id;
    private String userId;
    private Long carId;
    private LocalDate startDay;
    private LocalDate endDay;
}