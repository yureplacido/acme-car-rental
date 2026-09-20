package org.acme.rental.entity;

import io.quarkus.mongodb.panache.PanacheMongoEntity;

import java.time.LocalDate;

/**
 * Livro 7.6.1/7.6.2 - Aluguel como entidade Panache MongoDB (active record).
 *
 * <p>O {@code id} é um {@code ObjectId} autogerado (campo herdado de
 * {@link PanacheMongoEntity}); o reservation-service o trata como String (hex) —
 * ver seção 7.6.3. É apenas persistência: o domínio/API usam {@code model/Rental}
 * via {@code RentalMapper}; as consultas vivem no repositório, não na entidade.</p>
 */
public class RentalEntity extends PanacheMongoEntity {

    public String userId;
    public Long reservationId;
    public LocalDate startDate;
    public LocalDate endDate;
    public boolean active;
}