package org.acme.inventory.adapter.out.persistence;

import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleStatus;

import java.math.BigDecimal;

@Entity
@Table(name = "cars")
public class VehicleEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    public Long id;
    public String manufacturer;
    public String model;
    public String licensePlateNumber;

    @Enumerated(EnumType.STRING)
    public VehicleStatus status;
    @Enumerated(EnumType.STRING)
    public VehicleCategory category;
    @Enumerated(EnumType.STRING)
    public Transmission transmission;
    @Enumerated(EnumType.STRING)
    public FuelType fuelType;

    public Integer year;
    public String color;
    public Integer seats;

    // Transitional persistence field. Pricing ownership moves to the Pricing context.
    public BigDecimal dailyRate;

    public String branchCode;
    public String city;
}
