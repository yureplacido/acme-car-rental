package org.acme.billing.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

public record InvoiceLine(String description, int quantity, Money unitPrice) {

    public InvoiceLine {
        Objects.requireNonNull(description, "description is required");
        Objects.requireNonNull(unitPrice, "unit price is required");
        if (description.isBlank()) throw new IllegalArgumentException("description is required");
        if (quantity < 1) throw new IllegalArgumentException("quantity must be positive");
    }

    /**
     * Cria uma linha de locação com uma unidade por dia (inclusive), mínimo 1 dia.
     * Semântica idêntica ao livro (§9.4): {@code DAYS.between(start, end) + 1}.
     *
     * @param description descrição da linha (ex.: "Aluguel de veículo 7")
     * @param start       primeiro dia da locação
     * @param end         último dia da locação (inclusive)
     * @param unitPrice   tarifa diária acordada no booking (price lock)
     */
    public static InvoiceLine rentalDays(String description,
                                         LocalDate start,
                                         LocalDate end,
                                         Money unitPrice) {
        Objects.requireNonNull(start, "start date is required");
        Objects.requireNonNull(end, "end date is required");
        if (end.isBefore(start)) {
            throw new IllegalArgumentException("end date cannot precede start date");
        }
        long days = ChronoUnit.DAYS.between(start, end) + 1;
        int quantity = Math.max(1, Math.toIntExact(days));
        return new InvoiceLine(description, quantity, unitPrice);
    }

    public Money total() {
        return new Money(unitPrice.amount().multiply(BigDecimal.valueOf(quantity)), unitPrice.currency());
    }
}
