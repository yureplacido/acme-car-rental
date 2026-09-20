package org.acme.inventory.adapter.in.graphql;

import io.smallrye.graphql.api.Context;
import jakarta.inject.Inject;
import org.acme.inventory.adapter.in.graphql.model.RegisterVehicleInput;
import org.acme.inventory.adapter.in.graphql.model.SortOrder;
import org.acme.inventory.adapter.in.graphql.model.VehicleFilter;
import org.acme.inventory.adapter.in.graphql.model.VehiclePage;
import org.acme.inventory.adapter.in.graphql.model.VehicleSortField;
import org.acme.inventory.adapter.in.graphql.model.VehicleView;
import org.acme.inventory.application.usecase.DecommissionVehicle;
import org.acme.inventory.application.usecase.ListVehicles;
import org.acme.inventory.application.usecase.RegisterVehicle;
import org.acme.inventory.domain.model.FuelType;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Transmission;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleLocation;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.GraphQLApi;
import org.eclipse.microprofile.graphql.GraphQLException;
import org.eclipse.microprofile.graphql.Mutation;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.Query;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@GraphQLApi
@Description("API de inventário e gestão de frota")
public class GraphQLInventoryResource {

    private final ListVehicles listVehicles;
    private final RegisterVehicle registerVehicle;
    private final DecommissionVehicle decommissionVehicle;

    @Inject
    Context context;

    public GraphQLInventoryResource(ListVehicles listVehicles,
                                    RegisterVehicle registerVehicle,
                                    DecommissionVehicle decommissionVehicle) {
        this.listVehicles = listVehicles;
        this.registerVehicle = registerVehicle;
        this.decommissionVehicle = decommissionVehicle;
    }

    @Query("allCars")
    public List<VehicleView> cars(@Name("offset") Integer offset,
                                  @Name("limit") Integer limit,
                                  @Name("search") String search,
                                  @Name("filter") VehicleFilter filter,
                                  @Name("sort") VehicleSortField sort,
                                  @Name("order") SortOrder order) {
        System.out.println("Campos solicitados no inventário: " + context.getSelectedFields());
        return page(offset == null ? 0 : offset,
                limit == null ? VehiclePage.MAX_LIMIT : limit,
                search, filter, sort, order).getItems();
    }

    @Query("allCarsPage")
    public VehiclePage carsPage(@Name("offset") int offset,
                                @Name("limit") int limit,
                                @Name("search") String search,
                                @Name("filter") VehicleFilter filter,
                                @Name("sort") VehicleSortField sort,
                                @Name("order") SortOrder order) {
        return page(offset, limit, search, filter, sort, order);
    }

    @Query("findCar")
    public VehicleView findCarByPlate(@Name("plate") String plate) {
        return listVehicles.handle().stream()
                .filter(v -> v.licensePlate().value().equalsIgnoreCase(plate))
                .findFirst()
                .map(this::toView)
                .orElseThrow(() -> new GraphQLException("Carro com a placa " + plate + " não encontrado."));
    }

    @Mutation
    public VehicleView register(RegisterVehicleInput input) {
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
                        : new VehicleLocation(input.getBranchCode(), input.getCity()))));
    }

    @Mutation
    public boolean remove(@Name("plate") String plate) {
        return decommissionVehicle.handle(plate).isPresent();
    }

    private VehiclePage page(int offset, int limit, String search,
                             VehicleFilter filter, VehicleSortField sort, SortOrder order) {
        List<Vehicle> all = listVehicles.handle();
        List<Vehicle> matching = all.stream()
                .filter(matching(search, filter))
                .sorted(sortedBy(sort, order))
                .toList();

        int from = Math.max(0, offset);
        int size = Math.clamp(limit, 0, VehiclePage.MAX_LIMIT);
        List<VehicleView> items = matching.stream()
                .skip(from)
                .limit(size)
                .map(this::toView)
                .toList();

        return VehiclePage.builder()
                .items(items)
                .total(matching.size())
                .offset(from)
                .limit(size)
                .hasNextPage(from + size < matching.size())
                .build();
    }

    private Predicate<Vehicle> matching(String search, VehicleFilter filter) {
        String term = search == null ? "" : search.trim().toLowerCase(Locale.ROOT);
        return vehicle -> {
            boolean text = term.isEmpty()
                    || vehicle.licensePlate().value().toLowerCase(Locale.ROOT).contains(term)
                    || vehicle.specifications().manufacturer().toLowerCase(Locale.ROOT).contains(term)
                    || vehicle.specifications().model().toLowerCase(Locale.ROOT).contains(term);

            boolean filters = filter == null
                    || blankOrEquals(filter.getManufacturer(), vehicle.specifications().manufacturer())
                    && blankOrEquals(filter.getModel(), vehicle.specifications().model())
                    && blankOrEquals(filter.getPlate(), vehicle.licensePlate().value())
                    && (filter.getStatus() == null || filter.getStatus().isBlank()
                    || vehicle.status().name().equalsIgnoreCase(filter.getStatus()));

            boolean offered = filter != null && filter.getStatus() != null && !filter.getStatus().isBlank()
                    || vehicle.canBeOffered();

            return text && filters && offered;
        };
    }

    private boolean blankOrEquals(String expected, String actual) {
        return expected == null || expected.isBlank() || actual.equalsIgnoreCase(expected.trim());
    }

    private Comparator<Vehicle> sortedBy(VehicleSortField sort, SortOrder order) {
        Comparator<Vehicle> comparator = switch (sort == null ? VehicleSortField.ID : sort) {
            case PLATE_NUMBER -> Comparator.comparing(v -> v.licensePlate().value());
            case MANUFACTURER -> Comparator.comparing(v -> v.specifications().manufacturer());
            case MODEL -> Comparator.comparing(v -> v.specifications().model());
            case ID -> Comparator.comparing(v -> v.id().value());
        };
        return order == SortOrder.DESC ? comparator.reversed() : comparator;
    }

    private VehicleView toView(Vehicle vehicle) {
        return VehicleView.builder()
                .id(vehicle.id().value())
                .manufacturer(vehicle.specifications().manufacturer())
                .model(vehicle.specifications().model())
                .licensePlateNumber(vehicle.licensePlate().value())
                .status(vehicle.status().name())
                .category(vehicle.specifications().category() == null ? null : vehicle.specifications().category().name())
                .transmission(vehicle.specifications().transmission() == null ? null : vehicle.specifications().transmission().name())
                .fuelType(vehicle.specifications().fuelType() == null ? null : vehicle.specifications().fuelType().name())
                .year(vehicle.specifications().year())
                .color(vehicle.specifications().color())
                .seats(vehicle.specifications().seats())
                .branchCode(vehicle.location() == null ? null : vehicle.location().branchCode())
                .city(vehicle.location() == null ? null : vehicle.location().city())
                .build();
    }

    private static <T extends Enum<T>> T parseEnum(Class<T> type, String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return Enum.valueOf(type, value.trim().toUpperCase(Locale.ROOT));
    }
}
