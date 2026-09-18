package org.acme.reservation.reservation;

import io.quarkus.logging.Log;
import io.smallrye.graphql.client.GraphQLClient;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.reservation.inventory.Car;
import org.acme.reservation.inventory.DynamicInventoryClient;
import org.acme.reservation.inventory.GraphQLInventoryClient;
import org.acme.reservation.inventory.InventoryClient;
import org.acme.reservation.rental.Rental;
import org.acme.reservation.rental.RentalClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;
import java.util.Collection;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Path("/reservations") // Alterado para um path mais semântico e RESTful
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReservationResource {

    private final ReservationsRepository reservationsRepository;
    private final InventoryClient inventoryClient;
    private final DynamicInventoryClient dynamicInventoryClient;
    private final RentalClient rentalClient;

    public ReservationResource(ReservationsRepository reservations,
                               @GraphQLClient("inventory") GraphQLInventoryClient inventoryClient,
                               DynamicInventoryClient dynamicInventoryClient,
                               @RestClient RentalClient rentalClient) {
        this.reservationsRepository = reservations;
        this.inventoryClient = inventoryClient;
        this.dynamicInventoryClient = dynamicInventoryClient;
        this.rentalClient = rentalClient;
    }

    @GET
    @Path("availability")
    public Collection<Car> availability(@RestQuery LocalDate startDate,
                                        @RestQuery LocalDate endDate) {
        return availableCars(inventoryClient, startDate, endDate);
    }

    @GET
    @Path("availability/dynamic")
    public Collection<Car> availabilityDynamic(@RestQuery LocalDate startDate,
                                               @RestQuery LocalDate endDate) {
        return availableCars(dynamicInventoryClient, startDate, endDate);
    }

    private Collection<Car> availableCars(InventoryClient client,
                                          LocalDate startDate,
                                          LocalDate endDate) {
        Log.debugf("Verificando disponibilidade de veículos de %s até %s", startDate, endDate);

        // Transforma a lista de carros do cliente GraphQL em um mapa indexado por ID de forma funcional
        Map<Long, Car> carsById = client.allCars().stream()
                .collect(Collectors.toMap(Car::getId, Function.identity()));

        // Filtra e remove os carros que já possuem reservas sobrepostas no período selecionado
        reservationsRepository.findAll().stream()
                .filter(reservation -> reservation.isReserved(startDate, endDate))
                .forEach(reservation -> carsById.remove(reservation.getCarId()));

        return carsById.values();
    }

    @POST
    public Reservation make(Reservation reservation) {
        Log.infof("Processando nova reserva para o veículo ID: %d", reservation.getCarId());

        Reservation result = reservationsRepository.save(reservation);

        // Se a reserva inicia hoje, engatilha o fluxo assíncrono/REST com o serviço de aluguel (Rental)
        if (reservation.getStartDay().equals(LocalDate.now())) {
            triggerImmediateRental(result);
        }

        return result;
    }

    // Encapsulamento da lógica de negócio periférica para manter o método principal limpo
    private void triggerImmediateRental(Reservation reservation) {
        String defaultUserId = "anonymous_user"; // Evitar hardcoding puro sem contexto
        try {
            Log.infof("Reserva iniciando hoje. Solicitando ativação de aluguel imediato para o usuário: %s", defaultUserId);
            Rental rental = rentalClient.start(defaultUserId, reservation.getId());
            Log.infof("Aluguel iniciado com sucesso! Detalhes do registro: %s", rental);
        } catch (Exception e) {
            Log.errorf(e, "Falha ao iniciar o aluguel imediatamente para a reserva ID: %d. O fluxo principal continuará.", reservation.getId());
        }
    }
}
