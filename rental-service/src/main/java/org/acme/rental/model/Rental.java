package org.acme.rental.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Modelo de domínio do aluguel — POJO puro. É o tipo público do REST
 * ({@code /rental}); a persistência vive em {@code entity/RentalEntity} (Panache
 * MongoDB), mapeada por {@code RentalMapper}. Assim o Active Record ou o Repository
 * pattern podem mudar sem tocar no modelo/API.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class Rental {

    /** Id do documento, serializado como hex do ObjectId (formato do livro 7.6.3). */
    private String id;
    private String userId;
    private Long reservationId;
    private LocalDate startDate;
    private LocalDate endDate;
    private boolean active;
}