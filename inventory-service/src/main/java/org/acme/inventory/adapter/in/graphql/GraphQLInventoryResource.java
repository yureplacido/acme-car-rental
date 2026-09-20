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
import org.acme.inventory.application.query.VehiclePage;
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
import java.util.Optional;
import java.util.Locale;

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

    @Query("allCarsPage")
    public Uni<Page<Car>> carsPage(@Name("offset") @DefaultValue("0") int offset,
                              @Name("limit") @DefaultValue("20") int limit,
                              @Name("search") String search,
                              @Name("filter") CarFilter filter,
                              @Name("sort") @DefaultValue("ID") CarSortField sort,
                              @Name("order") @DefaultValue("ASC") SortOrder order) {
        return toPage(offset, limit, search, filter, sort, order);
    }

    @Query("findCar")
    public Uni<Car> findCarByPlate(@Name("plate") String plate) {
        return findVehicleByPlate.handle(plate)
                .flatMap(optional -> optional
                        .map(vehicle -> Uni.createFrom().item(toView(vehicle)))
                        .orElseGet(() -> Uni.createFrom().failure(
                                new GraphQLException("Carro com a placa " + plate + " não encontrado."))));
    }

    @Mutation
    public Uni<Car> register(CarInput input) {
        return toView(registerVehicle.handle(new RegisterVehicle.Command(
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
                        : input.getCurrency())));
    }

    @Mutation
    public Uni<Boolean> remove(@Name("plate") String plate) {
        return decommissionVehicle.handle(plate)
                .map(Optional::isPresent);
    }

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

    private VehicleSortField mapSort(CarSortField sort) {
        return switch (sort == null ? CarSortField.ID : sort) {
            case PLATE_NUMBER -> VehicleSortField.LICENSE_PLATE;
            case MANUFACTURER -> VehicleSortField.MANUFACTURER;
            case MODEL -> VehicleSortField.MODEL;
            case ID -> VehicleSortField.ID;
        };
    }

    private SortDirection mapDirection(SortOrder order) {
        return order == SortOrder.DESC ? SortDirection.DESC : SortDirection.ASC;
    }

    private String normalize(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

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

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }
}
