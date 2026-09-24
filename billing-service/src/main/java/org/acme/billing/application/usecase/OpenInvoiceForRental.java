package org.acme.billing.application.usecase;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.billing.application.port.out.InvoiceRepository;
import org.acme.billing.domain.model.Invoice;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;

import java.time.LocalDate;
import java.util.List;

/**
 * Abre a fatura DRAFT criada quando {@code ReservationConfirmed} foi recebido.
 * O evento {@code RentalCompleted} traz as datas efetivas e o snapshot da taxa
 * diária (price lock); a linha é recomposta com os dias reais da locação e a
 * fatura é aberta. A invoice é localizada pelo {@code reservationId}.
 */
@ApplicationScoped
public class OpenInvoiceForRental {

    private final InvoiceRepository repository;

    @Inject
    public OpenInvoiceForRental(InvoiceRepository repository) {
        this.repository = repository;
    }

    public Uni<Invoice> handle(Command command) {
        RentalDetails details = command.details();
        Money dailyRate = details.dailyRate();
        InvoiceLine rental = InvoiceLine.rentalDays(
                "Aluguel de veículo " + details.licensePlate(),
                details.startDate(),
                details.endDate(),
                dailyRate);
        return repository.findByReservationId(details.reservationId())
                .map(maybe -> maybe.orElseThrow(() ->
                        new IllegalStateException("No draft invoice for reservation: " + details.reservationId())))
                .map(invoice -> invoice.replaceLines(List.of(rental)).open())
                .flatMap(repository::save);
    }

    public record Command(RentalDetails details) {
    }

    public record RentalDetails(
            String reservationId,
            LocalDate startDate,
            LocalDate endDate,
            Money dailyRate,
            String licensePlate) {
    }
}