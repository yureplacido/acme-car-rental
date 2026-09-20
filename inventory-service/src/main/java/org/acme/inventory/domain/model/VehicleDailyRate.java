package org.acme.inventory.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record VehicleDailyRate(BigDecimal amount, String currency) {

    public VehicleDailyRate {
        Objects.requireNonNull(amount, "daily rate amount is required");
        Objects.requireNonNull(currency, "daily rate currency is required");
        if (amount.signum() <= 0) {
            throw new IllegalArgumentException("daily rate amount must be positive");
        }
        if (currency.isBlank()) {
            throw new IllegalArgumentException("daily rate currency is required");
        }
    }

    public static VehicleDailyRate brl(BigDecimal amount) {
        return new VehicleDailyRate(amount, "BRL");
    }
}
