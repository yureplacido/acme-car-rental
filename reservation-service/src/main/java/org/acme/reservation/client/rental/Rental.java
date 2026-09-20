package org.acme.reservation.client.rental;

import java.time.LocalDate;

/**
 * DTO do aluguel vindo do rental-service. Livro 7.6.3: o {@code id} passou de Long
 * para String, pois agora reflete o {@code ObjectId} gerado pelo MongoDB (serializado
 * como string no JSON) em vez de um contador numérico.
 */
public class Rental {

    public String id;
    public String userId;
    public Long reservationId;
    public LocalDate startDate;

    @Override
    public String toString() {
        return "Rental{" +
                "id='" + id + '\'' +
                ", userId='" + userId + '\'' +
                ", reservationId=" + reservationId +
                ", startDate=" + startDate +
                '}';
    }
}