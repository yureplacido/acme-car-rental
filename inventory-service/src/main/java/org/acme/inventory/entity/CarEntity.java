package org.acme.inventory.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.acme.inventory.model.graphql.CarStatus;
import org.acme.inventory.model.graphql.Category;
import org.acme.inventory.model.graphql.FuelType;
import org.acme.inventory.model.graphql.Transmission;

import java.math.BigDecimal;

/**
 * Entidade JPA (Panache) do veículo — só persistência sobre MySQL.
 * O domínio/API usam {@code model.Car}; a conversão fica em {@code CarMapper}.
 * Campos públicos é o estilo Panache; sem Lombok.
 */
@Entity
@Table(name = "cars") // Lowercase explícito para casar com o import.sql no MySQL (case-sensitive)
public class CarEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;

    public String manufacturer;

    public String model;

    public String licensePlateNumber;

    @Enumerated(EnumType.STRING)
    public CarStatus status;

    @Enumerated(EnumType.STRING)
    public Category category;

    @Enumerated(EnumType.STRING)
    public Transmission transmission;

    @Enumerated(EnumType.STRING)
    public FuelType fuelType;

    public Integer year;

    public String color;

    public Integer seats;

    public BigDecimal dailyRate;
}