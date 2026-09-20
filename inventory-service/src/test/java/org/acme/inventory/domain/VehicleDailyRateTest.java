package org.acme.inventory.domain;

import org.acme.inventory.domain.model.VehicleDailyRate;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class VehicleDailyRateTest {

    @Test
    void shouldCreatePositiveDailyRate() {
        VehicleDailyRate rate = VehicleDailyRate.brl(new BigDecimal("149.90"));

        assertEquals(new BigDecimal("149.90"), rate.amount());
        assertEquals("BRL", rate.currency());
    }

    @Test
    void shouldRejectNonPositiveDailyRate() {
        assertThrows(IllegalArgumentException.class,
                () -> VehicleDailyRate.brl(BigDecimal.ZERO));
    }
}
