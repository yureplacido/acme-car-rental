package org.acme.users.model;

/**
 * Cópia simplificada do Car do reservation/inventory service (livro 6.6),
 * usada pelo users-service para renderizar a lista de carros disponíveis.
 */
public class Car {
    public Long id;
    public String licensePlateNumber;
    public String manufacturer;
    public String model;
}