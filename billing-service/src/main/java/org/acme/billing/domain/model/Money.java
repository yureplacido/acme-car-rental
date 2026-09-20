package org.acme.billing.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record Money(BigDecimal amount, String currency) {

    public Money {
        Objects.requireNonNull(amount, "amount is required");
        Objects.requireNonNull(currency, "currency is required");
        if (amount.signum() < 0) throw new IllegalArgumentException("amount cannot be negative");
        if (currency.isBlank()) throw new IllegalArgumentException("currency is required");
    }

    public static Money brl(BigDecimal amount) {
        return new Money(amount, "BRL");
    }

    public Money add(Money other) {
        requireSameCurrency(other);
        return new Money(amount.add(other.amount), currency);
    }

    private void requireSameCurrency(Money other) {
        if (!currency.equals(other.currency)) {
            throw new IllegalArgumentException("money currencies must match");
        }
    }
}
