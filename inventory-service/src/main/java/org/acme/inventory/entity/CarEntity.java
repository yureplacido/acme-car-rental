package org.acme.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * Entidade JPA (Panache) do veículo — só persistência sobre MySQL.
 * O domínio/API usam {@code model.Car}; a conversão fica em {@code CarMapper}.
 * Campos públicos é o estilo Panache; sem Lombok.
 */
@Entity
@Table(name = "car") // Lowercase explícito para casar com o import.sql no MySQL (case-sensitive)
public class CarEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String manufacturer;

    public String model;

    public String licensePlateNumber;
}