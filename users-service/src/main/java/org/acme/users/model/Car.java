package org.acme.users.model;

import lombok.Builder;
import lombok.Data;

/**
 * Cópia simplificada do Car do reservation/inventory service (livro 6.6),
 * usada pelo users-service para renderizar a lista de carros disponíveis.
 */
@Data
@Builder
public class Car {
    private Long id;
    private String licensePlateNumber;
    private String manufacturer;
    private String model;
}