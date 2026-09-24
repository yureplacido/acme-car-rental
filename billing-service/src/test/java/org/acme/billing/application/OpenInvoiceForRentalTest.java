package org.acme.billing.application;

import io.smallrye.mutiny.Uni;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.application.usecase.OpenInvoiceForRental;
import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.InvoiceStatus;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OpenInvoiceForRentalTest {

    private FakeRepository repository;

    @BeforeEach
    void setUp() {
        repository = new FakeRepository();
    }

    @Test
    void shouldOpenDraftInvoiceForMatchedRental() {
        repository.saved = Invoice.draft("alice", "reservation-1",
                java.util.List.of(InvoiceLine.rentalDays("Aluguel de veículo 7",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 3),
                        Money.brl(new BigDecimal("150.00")))));
        OpenInvoiceForRental useCase = new OpenInvoiceForRental(repository);

        Invoice result = useCase.handle(new OpenInvoiceForRental.Command(
                new OpenInvoiceForRental.RentalDetails(
                        "reservation-1",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 3),
                        Money.brl(new BigDecimal("150.00")),
                        "ABC-1234")))
                .await().atMost(Duration.ofSeconds(5));

        assertEquals(InvoiceStatus.OPEN, result.status());
        assertEquals(1, result.lines().size());
        assertEquals(3, result.lines().get(0).quantity());
        assertEquals(new BigDecimal("450.00"), result.total().amount());
        assertSame(result, repository.saved);
    }

    @Test
    void shouldNotOpenUnknownReservationInvoice() {
        OpenInvoiceForRental useCase = new OpenInvoiceForRental(repository);
        assertThrows(IllegalStateException.class, () -> useCase.handle(
                new OpenInvoiceForRental.Command(new OpenInvoiceForRental.RentalDetails(
                        "reservation-1",
                        LocalDate.of(2026, 10, 1),
                        LocalDate.of(2026, 10, 3),
                        Money.brl(new BigDecimal("150.00")),
                        "ABC-1234")))
                .await().atMost(Duration.ofSeconds(5)));
    }

    static class FakeRepository implements InvoiceRepository {
        Invoice saved;
        public Uni<Invoice> save(Invoice invoice) { saved = invoice; return Uni.createFrom().item(invoice); }
        public Uni<Optional<Invoice>> findByReservationId(String reservationId) {
            return Uni.createFrom().item(Optional.ofNullable(saved).filter(i -> i.reservationId().equals(reservationId)));
        }
    }
}