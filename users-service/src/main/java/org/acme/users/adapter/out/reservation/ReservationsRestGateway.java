package org.acme.users.adapter.out.reservation;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.WebApplicationException;
import org.acme.users.adapter.out.reservation.model.Car;
import org.acme.users.adapter.out.reservation.model.Reservation;
import org.acme.users.application.exception.AvailabilityUnavailable;
import org.acme.users.application.model.AvailableCar;
import org.acme.users.application.model.ReservationView;
import org.acme.users.application.port.out.ReservationsGateway;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.Collection;

@ApplicationScoped
public class ReservationsRestGateway implements ReservationsGateway {

    private static final Logger LOG = Logger.getLogger(ReservationsRestGateway.class);

    private final ReservationsClient client;
    private final AvailabilityClient availabilityClient;

    @Inject
    public ReservationsRestGateway(@RestClient ReservationsClient client,
                                   @RestClient AvailabilityClient availabilityClient) {
        this.client = client;
        this.availabilityClient = availabilityClient;
    }

    @Override
    public Collection<ReservationView> allReservations() {
        return client.allReservations().stream().map(this::toView).toList();
    }

    @Override
    public ReservationView create(ReservationView reservation) {
        return toView(client.create(Reservation.builder()
                .id(reservation.id())
                .userId(reservation.userId())
                .carId(reservation.carId())
                .startDay(reservation.startDay())
                .endDay(reservation.endDay())
                .status(reservation.status())
                .build()));
    }

    @Override
    public Collection<AvailableCar> availability(LocalDate startDate, LocalDate endDate) {
        try {
            return availabilityClient.availability(startDate, endDate)
                    .stream()
                    .map(this::toAvailableCar)
                    .toList();
        } catch (WebApplicationException | ProcessingException failure) {
            // Taxonomia medida contra o cliente real (ReservationsClientFailureTest): resposta
            // nao-2xx (ex.: o 503 do reservation-service) vem como ClientWebApplicationException,
            // um WebApplicationException; falha de transporte e estouro de prazo vem como
            // ProcessingException. Em ambos nao ha dado: o resultado e inconclusivo e nao pode
            // virar lista vazia.
            //
            // O log e obrigatorio, nao decorativo: o mapper inbound consome a excecao via
            // @ServerExceptionMapper, entao o Quarkus NAO a loga sozinho. Sem estas duas linhas
            // a causa (host, porta, status) se perde e o mascaramento de qualquer falha upstream
            // como 503 ficaria sem canal de diagnostico. Mesmo lugar e nivel do analogo
            // GraphQLInventoryGateway.inventoryUnreachable.
            LOG.warnf(failure,
                    "Reservation did not answer the vehicle availability; the result is unknown");
            throw new AvailabilityUnavailable(failure);
        }
    }

    private ReservationView toView(Reservation value) {
        return new ReservationView(
                value.getId(), value.getUserId(), value.getCarId(),
                value.getStartDay(), value.getEndDay(), value.getStatus());
    }

    private AvailableCar toAvailableCar(Car value) {
        return new AvailableCar(
                value.getId(), value.getLicensePlateNumber(),
                value.getManufacturer(), value.getModel());
    }
}
