package org.acme.reservation.api;

import io.quarkus.hibernate.reactive.panache.common.WithTransaction;
import io.quarkus.logging.Log;
import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import jakarta.ws.rs.BadRequestException;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.DefaultValue;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import org.acme.reservation.client.inventory.Car;
import org.acme.reservation.client.inventory.CarFilter;
import org.acme.reservation.client.inventory.CarPage;
import org.acme.reservation.client.inventory.CarSortField;
import org.acme.reservation.client.inventory.DynamicInventoryClient;
import org.acme.reservation.client.inventory.GraphQLInventoryClient;
import org.acme.reservation.client.inventory.InventoryQuery;
import org.acme.reservation.client.inventory.SortOrder;
import org.acme.reservation.application.ReservationApplicationService;
import org.acme.reservation.model.Reservation;
import org.acme.reservation.repository.ReservationRepository;
import org.acme.reservation.security.CurrentUser;
import org.eclipse.microprofile.rest.client.inject.RestClient;
import org.jboss.resteasy.reactive.RestQuery;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Livro 7.1 ampliado: entidade Panache ativa (7.1/7.4) com acesso reativo (7.7).
 * Métodos que tocam o banco retornam {@link Uni} e usam {@link WithTransaction};
 * os endpoints de consulta ao inventário (sem banco) seguem imperativos, o que é
 * permitido pelo Quarkus misturar no mesmo resource.
 */
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

    private final GraphQLInventoryClient inventoryClient;
    private final DynamicInventoryClient dynamicInventoryClient;
    private final ReservationApplicationService reservationApplicationService;
    private final ReservationRepository reservationRepository;

    // Injeção mista: CurrentUser por campo + construtor para os demais
    @Inject
    CurrentUser currentUser;

    public ReservationResource(@GraphQLClient("inventory") GraphQLInventoryClient inventoryClient,
                               DynamicInventoryClient dynamicInventoryClient,
                               @RestClient org.acme.reservation.client.rental.RentalClient rentalClient,
                               ReservationRepository reservationRepository,
                               ReservationApplicationService reservationApplicationService) {
        this.inventoryClient = inventoryClient;
        this.dynamicInventoryClient = dynamicInventoryClient;
        this.rentalClient = rentalClient;
        this.reservationRepository = reservationRepository;
        this.reservationApplicationService = reservationApplicationService;
    }

    /**
     * Lista as reservas do usuário autenticado (principal). Sem usuário (anônimo),
     * devolve todas — simplificação do livro 6.2.1 para desenvolvimento.
     */
    @GET
    @Path("all")
    public Uni<List<Reservation>> allReservations() {
        String userId = userId();
        return reservationRepository.all()
                .onItem().transform(reservations -> reservations.stream()
                        .filter(reservation -> userId == null || userId.equals(reservation.getUserId()))
                        .collect(Collectors.toList()));
    }

    private String userId() {
        return currentUser.getUserId();
    }

    @GET
    @Path("availability")
    public Uni<Collection<Car>> availability(@RestQuery LocalDate startDate,
                                             @RestQuery LocalDate endDate) {
        Log.debugf("Verificando disponibilidade de veículos de %s até %s", startDate, endDate);
        return availableCars(inventoryClient.allCars(), startDate, endDate);
    }

    @GET
    @Path("availability/dynamic")
    public Uni<Collection<Car>> availabilityDynamic(@RestQuery LocalDate startDate,
                                                    @RestQuery LocalDate endDate,
                                                    @RestQuery @DefaultValue("id,plateNumber,manufacturer,model") String fields) {
        List<String> projected = parseFields(fields);
        return availableCars(dynamicInventoryClient.allAsync(projected), startDate, endDate);
    }

    /**
     * Livro 7.41: executa as duas fontes (inventário GraphQL + reservas do banco)
     * em paralelo e combina os {@link Uni} para filtrar os carros disponíveis.
     */
    private Uni<Collection<Car>> availableCars(Uni<List<Car>> carsUni,
                                               LocalDate startDate,
                                               LocalDate endDate) {
        Uni<List<Reservation>> reservationsUni = reservationRepository.all();
        return Uni.combine().all().unis(carsUni, reservationsUni)
                .with((availableCars, reservations) -> {
                    Map<Long, Car> carsById = new HashMap<>();
                    for (Car car : availableCars) {
                        carsById.put(car.getId(), car);
                    }
                    for (Reservation reservation : reservations) {
                        if (reservation.isReserved(startDate, endDate)) {
                            carsById.remove(reservation.getCarId());
                        }
                    }
                    return carsById.values();
                });
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

    /**
     * Livro 7.38: persiste a reserva reativamente; se ela começa hoje, dispara o
     * aluguel imediato chamando o RentalClient (também reativo) e devolve a reserva.
     */
    @POST
    @WithTransaction
    public Uni<Reservation> make(Reservation reservation) {
        Log.infof("Delegando criação da reserva para a camada de aplicação. veículo ID: %d", reservation.getCarId());
        return reservationApplicationService.create(reservation, currentUser.getUserId())
                .onItem().invoke(persistedReservation ->
                        Log.infof("Successfully reserved reservation %s", persistedReservation));
    }
}