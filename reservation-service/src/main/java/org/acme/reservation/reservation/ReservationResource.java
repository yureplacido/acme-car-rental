package org.acme.reservation.reservation;

import io.quarkus.logging.Log;
import io.smallrye.graphql.client.GraphQLClient;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.reservation.inventory.Car;
import org.acme.reservation.inventory.CarFilter;
import org.acme.reservation.inventory.CarPage;
import org.acme.reservation.inventory.CarSortField;
import org.acme.reservation.inventory.DynamicInventoryClient;
import org.acme.reservation.inventory.GraphQLInventoryClient;
import org.acme.reservation.inventory.InventoryQuery;
import org.acme.reservation.inventory.SortOrder;
import org.acme.reservation.rental.Rental;
import org.acme.reservation.rental.RentalClient;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

@Path("/reservations") // Alterado para um path mais semântico e RESTful
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ReservationResource {

    private static final Set<String> ALLOWED_FIELDS = Set.of("id", "plateNumber", "manufacturer", "model");
    private static final Map<String, CarSortField> SORT_FIELDS = Map.of(
            "id", CarSortField.ID,
            "platenumber", CarSortField.PLATE_NUMBER,
            "manufacturer", CarSortField.MANUFACTURER,
            "model", CarSortField.MODEL);
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int MAX_LIMIT = 100;

    private final ReservationsRepository reservationsRepository;
    private final GraphQLInventoryClient inventoryClient;
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
        return availableCars(inventoryClient::allCars, startDate, endDate);
    }

    @GET
    @Path("availability/dynamic")
    public Collection<Car> availabilityDynamic(@RestQuery LocalDate startDate,
                                               @RestQuery LocalDate endDate,
                                               @RestQuery @DefaultValue("id,plateNumber,manufacturer,model") String fields) {
        List<String> projected = parseFields(fields);
        return availableCars(() -> dynamicInventoryClient.all(projected), startDate, endDate);
    }

    @GET
    @Path("inventory")
    public List<Car> inventory(@RestQuery @DefaultValue("0") Integer offset,
                               @RestQuery Integer limit,
                               @RestQuery @DefaultValue("id,plateNumber,manufacturer,model") String fields,
                               @RestQuery String q,
                               @RestQuery String manufacturer,
                               @RestQuery String model,
                               @RestQuery String plate,
                               @RestQuery String sort,
                               @RestQuery String order) {
        return dynamicInventoryClient.page(query(offset, limit, fields, q, manufacturer, model, plate, sort, order));
    }

    @GET
    @Path("inventory/pages")
    public CarPage inventoryPage(@RestQuery @DefaultValue("0") Integer offset,
                                 @RestQuery Integer limit,
                                 @RestQuery @DefaultValue("id,plateNumber,manufacturer,model") String fields,
                                 @RestQuery String q,
                                 @RestQuery String manufacturer,
                                 @RestQuery String model,
                                 @RestQuery String plate,
                                 @RestQuery String sort,
                                 @RestQuery String order) {
        return dynamicInventoryClient.carPage(query(offset, limit, fields, q, manufacturer, model, plate, sort, order));
    }

    // --- Validação da fachada REST ---

    private int validateOffset(Integer offset) {
        if (offset != null && offset < 0) {
            throw new BadRequestException("offset deve ser um valor não negativo");
        }
        return offset == null ? 0 : offset;
    }

    private int validateLimit(Integer limit) {
        if (limit == null) {
            return DEFAULT_PAGE_SIZE;
        }
        if (limit < 1) {
            throw new BadRequestException("limit deve ser um valor positivo");
        }
        return Math.min(limit, MAX_LIMIT);
    }

    private List<String> parseFields(String fields) {
        List<String> parsed = Arrays.stream(fields.split(","))
                .map(String::trim)
                .filter(name -> !name.isEmpty())
                .toList();
        List<String> unknown = parsed.stream()
                .filter(name -> !ALLOWED_FIELDS.contains(name))
                .toList();
        if (!unknown.isEmpty()) {
            throw new BadRequestException("Campos inválidos: " + unknown + ". Permitidos: " + ALLOWED_FIELDS);
        }
        return parsed;
    }

    private InventoryQuery query(Integer offset, Integer limit, String fields,
                                 String q, String manufacturer, String model, String plate,
                                 String sort, String order) {
        CarFilter filter = new CarFilter(norm(manufacturer), norm(model), norm(plate));
        return new InventoryQuery(validateOffset(offset), validateLimit(limit),
                norm(q), filter,
                parseSort(sort), parseOrder(order),
                parseFields(fields));
    }

    private CarSortField parseSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return null;
        }
        CarSortField field = SORT_FIELDS.get(sort.trim().toLowerCase());
        if (field == null) {
            throw new BadRequestException("sort inválido: " + sort + ". Permitidos: id, plateNumber, manufacturer, model");
        }
        return field;
    }

    private SortOrder parseOrder(String order) {
        if (order == null || order.isBlank()) {
            return null;
        }
        return switch (order.trim().toLowerCase()) {
            case "asc" -> SortOrder.ASC;
            case "desc" -> SortOrder.DESC;
            default -> throw new BadRequestException("order deve ser 'asc' ou 'desc'");
        };
    }

    private String norm(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private Collection<Car> availableCars(Supplier<List<Car>> carsSupplier,
                                          LocalDate startDate,
                                          LocalDate endDate) {
        Log.debugf("Verificando disponibilidade de veículos de %s até %s", startDate, endDate);

        // Transforma a lista de carros do cliente GraphQL em um mapa indexado por ID de forma funcional
        Map<Long, Car> carsById = carsSupplier.get().stream()
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
