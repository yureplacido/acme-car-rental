package org.acme.inventory.adapter.in.graphql;

import io.smallrye.graphql.api.Context;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.acme.inventory.adapter.in.graphql.model.Car;
import org.acme.inventory.adapter.in.graphql.model.CarFilter;
import org.acme.inventory.adapter.in.graphql.model.CarInput;
import org.acme.inventory.adapter.in.graphql.model.CarSortField;
import org.acme.inventory.adapter.in.graphql.model.Page;
import org.acme.inventory.adapter.in.graphql.model.SortOrder;
import org.acme.inventory.application.query.SortDirection;
import org.acme.inventory.application.query.VehicleFilter;
import org.acme.inventory.application.query.VehicleSearch;
import org.acme.inventory.application.query.VehicleSortField;
import org.acme.inventory.application.usecase.DecommissionVehicle;
import org.acme.inventory.application.usecase.FindVehicleByPlate;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.application.usecase.SearchVehicles;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleLocation;
import org.acme.inventory.domain.model.VehicleStatus;
import org.eclipse.microprofile.graphql.DefaultValue;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import java.util.List;
import java.util.Locale;
import java.util.Optional;

@GraphQLApi
@Description("API de inventário e gestão de frota")
public class GraphQLInventoryResource {

    private final SearchVehicles searchVehicles;
    private final FindVehicleByPlate findVehicleByPlate;
    private final RegisterVehicle registerVehicle;
    private final DecommissionVehicle decommissionVehicle;

    @Inject
    Context context;

    public GraphQLInventoryResource(SearchVehicles searchVehicles,
                                    FindVehicleByPlate findVehicleByPlate,
                                    RegisterVehicle registerVehicle,
                                    DecommissionVehicle decommissionVehicle) {
        this.searchVehicles = searchVehicles;
        this.findVehicleByPlate = findVehicleByPlate;
        this.registerVehicle = registerVehicle;
        this.decommissionVehicle = decommissionVehicle;
    }

    /**
     * Lista os carros do inventário, aplicando paginação, busca textual,
     * filtros, ordenação e direção.
     *
     * <p>Retorna apenas a lista de itens (sem metadados de paginação).
     * Para obter os metadados (total, offset, limit, hasNextPage), use
     * {@code allCarsPage}.</p>
     *
     * <p>Exemplos de uso:</p>
     *
     * <pre>{@code
     * # Lista simples (usa defaults: offset=0, limit=100, sort=ID, order=ASC)
     * query {
     *   allCars {
     *     id
     *     manufacturer
     *     model
     *     licensePlateNumber
     *     status
     *   }
     * }
     *
     * # Paginação + busca textual
     * query {
     *   allCars(offset: 0, limit: 10, search: "ford") {
     *     id
     *     manufacturer
     *     model
     *     licensePlateNumber
     *   }
     * }
     *
     * # Filtro + ordenação
     * query {
     *   allCars(
     *     filter: { manufacturer: "Ford", status: AVAILABLE }
     *     sort: PLATE_NUMBER
     *     order: ASC
     *   ) {
     *     licensePlateNumber
     *     manufacturer
     *     status
     *   }
     * }
     * }</pre>
     *
     * @param offset deslocamento inicial (default 0); valores negativos são normalizados para 0
     * @param limit  quantidade máxima de itens (default 100); valores menores que 1 são normalizados para 1
     * @param search termo de busca textual (aplicado sobre manufacturer/model/plate)
     * @param filter filtros estruturados (manufacturer, model, plate, status)
     * @param sort   campo de ordenação (default ID)
     * @param order  direção da ordenação (default ASC)
     * @return lista reativa de carros
     */
    @Query("allCars")
    public Uni<List<Car>> cars(@Name("offset") @DefaultValue("0") Integer offset,
                               @Name("limit") @DefaultValue("100") Integer limit,
                               @Name("search") String search,
                               @Name("filter") CarFilter filter,
                               @Name("sort") @DefaultValue("ID") CarSortField sort,
                               @Name("order") @DefaultValue("ASC") SortOrder order) {
        System.out.println("Campos solicitados no inventário: " + context.getSelectedFields());
        return toPage(offset, limit, search, filter, sort, order).map(Page::getItems);
    }

    /**
     * Lista os carros do inventário retornando também os metadados de paginação
     * ({@code total}, {@code offset}, {@code limit}, {@code hasNextPage}).
     *
     * <p>Útil para UIs que precisam montar controles de paginação.</p>
     *
     * <p>Exemplos de uso:</p>
     *
     * <pre>{@code
     * # Página inicial com metadados
     * query {
     *   allCarsPage(offset: 0, limit: 20) {
     *     total
     *     offset
     *     limit
     *     hasNextPage
     *     items {
     *       id
     *       licensePlateNumber
     *       manufacturer
     *       model
     *       status
     *     }
     *   }
     * }
     *
     * # Segunda página filtrando por status
     * query {
     *   allCarsPage(
     *     offset: 20
     *     limit: 20
     *     filter: { status: AVAILABLE }
     *     sort: MANUFACTURER
     *     order: DESC
     *   ) {
     *     total
     *     hasNextPage
     *     items {
     *       licensePlateNumber
     *       manufacturer
     *     }
     *   }
     * }
     * }</pre>
     *
     * @param offset deslocamento inicial (default 0)
     * @param limit  quantidade máxima de itens (default 20)
     * @param search termo de busca textual
     * @param filter filtros estruturados
     * @param sort   campo de ordenação (default ID)
     * @param order  direção da ordenação (default ASC)
     * @return página reativa de carros com metadados
     */
    @Query("allCarsPage")
    public Uni<Page<Car>> carsPage(@Name("offset") @DefaultValue("0") int offset,
                                   @Name("limit") @DefaultValue("20") int limit,
                                   @Name("search") String search,
                                   @Name("filter") CarFilter filter,
                                   @Name("sort") @DefaultValue("ID") CarSortField sort,
                                   @Name("order") @DefaultValue("ASC") SortOrder order) {
        return toPage(offset, limit, search, filter, sort, order);
    }

    /**
     * Busca um carro pela placa. Retorna falha ({@link GraphQLException}) caso
     * não exista veículo com a placa informada.
     *
     * <p>Exemplos de uso:</p>
     *
     * <pre>{@code
     * # Busca por placa
     * query {
     *   findCar(plate: "AAA111") {
     *     id
     *     licensePlateNumber
     *     manufacturer
     *     model
     *     status
     *     dailyRate
     *   }
     * }
     *
     * # Resposta de erro (quando não encontrado)
     * # {
     * #   "errors": [
     * #     { "message": "Carro com a placa XXX999 não encontrado." }
     * #   ]
     * # }
     * }</pre>
     *
     * @param plate placa do veículo
     * @return carro encontrado ou falha com {@link GraphQLException}
     */
    @Query("findCar")
    public Uni<Car> findCarByPlate(@Name("plate") String plate) {
        return findVehicleByPlate.handle(plate)
                .flatMap(optional -> optional
                        .map(vehicle -> Uni.createFrom().item(toView(vehicle)))
                        .orElseGet(() -> Uni.createFrom().failure(
                                new GraphQLException("Carro com a placa " + plate + " não encontrado."))));
    }

    /**
     * Registra um novo veículo no inventário a partir de um {@link CarInput}.
     *
     * <p>Regras de normalização aplicadas:</p>
     * <ul>
     *   <li>{@code category}, {@code transmission} e {@code fuelType} são convertidos
     *       para enums (case-insensitive, com {@code trim}).</li>
     *   <li>{@code location} só é criada se {@code branchCode} e {@code city} forem informados.</li>
     *   <li>{@code currency} assume {@code "BRL"} quando nulo ou em branco.</li>
     * </ul>
     *
     * <p>Exemplo de uso:</p>
     *
     * <pre>{@code
     * mutation {
     *   register(input: {
     *     licensePlateNumber: "AAA111"
     *     manufacturer: "Ford"
     *     model: "Mustang"
     *     category: "SUV"
     *     transmission: "AUTOMATIC"
     *     fuelType: "GASOLINE"
     *     year: 2025
     *     color: "black"
     *     seats: 5
     *     branchCode: "SP01"
     *     city: "São Paulo"
     *     dailyRate: 350.00
     *     currency: "BRL"
     *   }) {
     *     id
     *     licensePlateNumber
     *     manufacturer
     *     model
     *     status
     *     dailyRate
     *   }
     * }
     * }</pre>
     *
     * @param input dados do veículo a registrar
     * @return carro persistido
     */
    @Mutation
    public Uni<Car> register(CarInput input) {
        return registerVehicle.handle(new RegisterVehicle.Command(
                        input.getLicensePlateNumber(),
                        input.getManufacturer(),
                        input.getModel(),
                        parseEnum(VehicleCategory.class, input.getCategory()),
                        parseEnum(Transmission.class, input.getTransmission()),
                        parseEnum(FuelType.class, input.getFuelType()),
                        input.getYear(),
                        input.getColor(),
                        input.getSeats(),
                        input.getBranchCode() == null || input.getCity() == null
                                ? null
                                : new VehicleLocation(input.getBranchCode(), input.getCity()),
                        input.getDailyRate(),
                        input.getCurrency() == null || input.getCurrency().isBlank()
                                ? "BRL"
                                : input.getCurrency()))
                .map(this::toView);
    }

    /**
     * Descomissiona (remove logicamente) um veículo pela placa.
     *
     * <p>Retorna {@code true} se o veículo foi encontrado e descomissionado,
     * {@code false} caso contrário.</p>
     *
     * <p>Exemplo de uso:</p>
     *
     * <pre>{@code
     * mutation {
     *   remove(plate: "AAA111")
     * }
     * }</pre>
     *
     * @param plate placa do veículo a descomissionar
     * @return {@code true} se removido, {@code false} se não encontrado
     */
    @Mutation
    public Uni<Boolean> remove(@Name("plate") String plate) {
        return decommissionVehicle.handle(plate)
                .map(Optional::isPresent);
    }

    /**
     * Converte os parâmetros de entrada GraphQL em uma {@link VehicleSearch},
     * executa a busca e monta a {@link Page} de resposta.
     *
     * <p>Aplica saneamento em {@code offset} (mínimo 0) e {@code limit} (mínimo 1).</p>
     *
     * <p>Equivale internamente a:</p>
     * <pre>{@code
     * searchVehicles.handle(new VehicleSearch(
     *     max(0, offset),
     *     max(1, limit),
     *     search,
     *     toFilter(filter),
     *     mapSort(sort),
     *     mapDirection(order)));
     * }</pre>
     *
     * @param offset deslocamento inicial
     * @param limit  quantidade máxima de itens
     * @param search termo de busca textual
     * @param filter filtros estruturados
     * @param sort   campo de ordenação
     * @param order  direção da ordenação
     * @return página reativa de carros
     */
    private Uni<Page<Car>> toPage(int offset,
                                  int limit,
                                  String search,
                                  CarFilter filter,
                                  CarSortField sort,
                                  SortOrder order) {
        return searchVehicles.handle(new VehicleSearch(
                        Math.max(0, offset),
                        Math.max(1, limit),
                        search,
                        toFilter(filter),
                        mapSort(sort),
                        mapDirection(order)))
                .map(result -> {
                    List<Car> items = result.items().stream().map(this::toView).toList();

                    return Page.<Car>builder()
                            .items(items)
                            .total(result.total())
                            .offset(result.offset())
                            .limit(result.limit())
                            .hasNextPage(result.hasNextPage())
                            .build();
                });
    }

    /**
     * Converte o {@link CarFilter} GraphQL em {@link VehicleFilter} de aplicação.
     *
     * <p>Strings são normalizadas (trim; vazio vira {@code null}). O status é
     * convertido pelo nome do enum.</p>
     *
     * @param filter filtro GraphQL (pode ser {@code null})
     * @return filtro de aplicação ou {@code null} se {@code filter} for {@code null}
     */
    private VehicleFilter toFilter(CarFilter filter) {
        if (filter == null) {
            return null;
        }

        return new VehicleFilter(
                normalize(filter.getManufacturer()),
                normalize(filter.getModel()),
                normalize(filter.getPlate()),
                filter.getStatus() == null
                        ? null
                        : VehicleStatus.valueOf(filter.getStatus().name()));
    }

    /**
     * Mapeia o enum de ordenação GraphQL ({@link CarSortField}) para o de
     * aplicação ({@link VehicleSortField}).
     *
     * <p>Se {@code sort} for {@code null}, assume {@link CarSortField#ID}.</p>
     *
     * @param sort campo de ordenação GraphQL
     * @return campo de ordenação de aplicação
     */
    private VehicleSortField mapSort(CarSortField sort) {
        return switch (sort == null ? CarSortField.ID : sort) {
            case PLATE_NUMBER -> VehicleSortField.LICENSE_PLATE;
            case MANUFACTURER -> VehicleSortField.MANUFACTURER;
            case MODEL -> VehicleSortField.MODEL;
            case ID -> VehicleSortField.ID;
        };
    }

    /**
     * Mapeia a direção GraphQL ({@link SortOrder}) para a de aplicação
     * ({@link SortDirection}).
     *
     * <p>Qualquer valor diferente de {@link SortOrder#DESC} resulta em {@code ASC}.</p>
     *
     * @param order direção GraphQL
     * @return direção de aplicação
     */
    private SortDirection mapDirection(SortOrder order) {
        return order == SortOrder.DESC ? SortDirection.DESC : SortDirection.ASC;
    }

    /**
     * Normaliza uma string: {@code null} ou em branco viram {@code null};
     * caso contrário, aplica {@code trim}.
     *
     * @param value valor de entrada
     * @return valor normalizado ou {@code null}
     */
    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * Converte o modelo de domínio {@link Vehicle} para o modelo GraphQL {@link Car}.
     *
     * <p>Faz o mapeamento de enums do domínio para os enums expostos na API
     * GraphQL (status, category, transmission, fuelType, condition) e trata
     * campos opcionais como {@code location} e {@code dailyRate}.</p>
     *
     * @param vehicle veículo de domínio
     * @return representação GraphQL do carro
     */
    private Car toView(Vehicle vehicle) {
        return Car.builder()
                .id(vehicle.id().value())
                .manufacturer(vehicle.specifications().manufacturer())
                .model(vehicle.specifications().model())
                .licensePlateNumber(vehicle.licensePlate().value())
                .status(org.acme.inventory.adapter.in.graphql.model.CarStatus.valueOf(vehicle.status().name()))
                .category(vehicle.specifications().category() == null
                        ? null
                        : org.acme.inventory.adapter.in.graphql.model.Category.valueOf(
                        vehicle.specifications().category().name()))
                .transmission(vehicle.specifications().transmission() == null
                        ? null
                        : org.acme.inventory.adapter.in.graphql.model.Transmission.valueOf(
                        vehicle.specifications().transmission().name()))
                .fuelType(vehicle.specifications().fuelType() == null
                        ? null
                        : org.acme.inventory.adapter.in.graphql.model.FuelType.valueOf(
                        vehicle.specifications().fuelType().name()))
                .year(vehicle.specifications().year())
                .color(vehicle.specifications().color())
                .seats(vehicle.specifications().seats())
                .branchCode(vehicle.location() == null ? null : vehicle.location().branchCode())
                .city(vehicle.location() == null ? null : vehicle.location().city())
                .dailyRate(vehicle.dailyRate() == null ? null : vehicle.dailyRate().amount())
                .odometerKm(vehicle.odometer().kilometers())
                .condition(org.acme.inventory.adapter.in.graphql.model.VehicleCondition.valueOf(
                        vehicle.condition().name()))
                .build();
    }

    /**
     * Converte uma string em um enum, de forma tolerante: {@code null} ou em
     * branco resultam em {@code null}; caso contrário, aplica {@code trim} e
     * {@code toUpperCase(Locale.ROOT)} antes do {@code Enum.valueOf}.
     *
     * <p>Exemplos:</p>
     * <pre>{@code
     * parseEnum(Transmission.class, "automatic") // Transmission.AUTOMATIC
     * parseEnum(VehicleCategory.class, " suv ")  // VehicleCategory.SUV
     * parseEnum(FuelType.class, null)            // null
     * }</pre>
     *
     * @param type  classe do enum
     * @param value valor textual
     * @return enum correspondente ou {@code null}
     */
    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }
}