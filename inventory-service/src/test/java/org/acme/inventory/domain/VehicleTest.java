package org.acme.inventory.domain;

import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleSpecifications;
import org.acme.inventory.domain.model.VehicleDailyRate;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTest {

    @Test
    void shouldRegisterVehicleAsAvailable() {
        Vehicle vehicle = Vehicle.register(
                new LicensePlate("abc123"),
                new VehicleSpecifications("Ford", "Mustang",
                        VehicleCategory.SUV, Transmission.AUTOMATIC,
                        null, 2025, "black", 5),
                null,
                VehicleDailyRate.brl(new BigDecimal("149.90")));

        assertTrue(vehicle.canBeOffered());
        assertEquals("ABC123", vehicle.licensePlate().value());
        assertEquals(new BigDecimal("149.90"), vehicle.dailyRate().amount());
    }

    @Test
    void shouldDecommissionVehicle() {
        Vehicle vehicle = Vehicle.register(
                new LicensePlate("abc123"),
                new VehicleSpecifications("Ford", "Mustang",
                        VehicleCategory.SUV, Transmission.AUTOMATIC,
                        null, 2025, "black", 5),
                null,
                VehicleDailyRate.brl(new BigDecimal("149.90")));

        vehicle.decommission();

        assertEquals(org.acme.inventory.domain.model.VehicleStatus.DECOMMISSIONED, vehicle.status());
        assertFalse(vehicle.canBeOffered());
    }

    @Test
    void shouldNotReleaseDecommissionedVehicleFromMaintenance() {
        Vehicle vehicle = Vehicle.register(
                new LicensePlate("abc123"),
                new VehicleSpecifications("Ford", "Mustang",
                        VehicleCategory.SUV, Transmission.AUTOMATIC,
                        null, 2025, "black", 5),
                null,
                VehicleDailyRate.brl(new BigDecimal("149.90")));

        vehicle.decommission();

        assertThrows(IllegalStateException.class, vehicle::releaseFromMaintenance);
    }
}
