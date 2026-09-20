package org.acme.inventory.domain;

import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.OdometerReading;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCondition;
import org.acme.inventory.domain.model.VehicleSpecifications;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.Transmission;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VehicleTelemetryTest {

    @Test
    void shouldStartWithZeroOdometerAndGoodCondition() {
        Vehicle vehicle = vehicle();

        assertEquals(new OdometerReading(0L), vehicle.odometer());
        assertEquals(VehicleCondition.GOOD, vehicle.condition());
    }

    @Test
    void shouldAdvanceOdometer() {
        Vehicle vehicle = vehicle();

        vehicle.recordOdometer(new OdometerReading(12500L));

        assertEquals(new OdometerReading(12500L), vehicle.odometer());
    }

    @Test
    void shouldNotAllowOdometerToGoBackwards() {
        Vehicle vehicle = vehicle();
        vehicle.recordOdometer(new OdometerReading(12500L));

        assertThrows(IllegalArgumentException.class,
                () -> vehicle.recordOdometer(new OdometerReading(12000L)));
    }

    @Test
    void shouldChangeVehicleCondition() {
        Vehicle vehicle = vehicle();

        vehicle.changeCondition(VehicleCondition.NEEDS_INSPECTION);

        assertEquals(VehicleCondition.NEEDS_INSPECTION, vehicle.condition());
    }

    private Vehicle vehicle() {
        return Vehicle.register(
                new LicensePlate("abc123"),
                new VehicleSpecifications("Ford", "Mustang",
                        VehicleCategory.SUV, Transmission.AUTOMATIC,
                        null, 2025, "black", 5),
                null,
                null);
    }
}
