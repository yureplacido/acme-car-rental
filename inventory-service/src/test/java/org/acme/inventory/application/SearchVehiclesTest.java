package org.acme.inventory.application;

import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.query.SortDirection;
import org.acme.inventory.application.query.VehicleFilter;
import org.acme.inventory.application.query.VehicleSearch;
import org.acme.inventory.application.query.VehicleSortField;
import org.acme.inventory.application.usecase.SearchVehicles;
import org.acme.inventory.domain.model.LicensePlate;
import org.acme.inventory.domain.model.Vehicle;
import org.acme.inventory.domain.model.VehicleCategory;
import org.acme.inventory.domain.model.VehicleSpecifications;
import org.acme.inventory.domain.model.VehicleStatus;
import org.acme.inventory.domain.model.Transmission;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SearchVehiclesTest {

    private final FakeVehicleRepository repository = new FakeVehicleRepository();
    private final SearchVehicles search = new SearchVehicles(repository);

    @Test
    void shouldSearchVehiclesByManufacturer() {
        repository.items.add(vehicle("AAA111", "Ford", "Mustang", 1L));
        repository.items.add(vehicle("BBB222", "Toyota", "Corolla", 2L));

        var result = search.handle(new VehicleSearch(
                0, 100, "ford",
                new VehicleFilter(null, null, null, null),
                VehicleSortField.ID, SortDirection.ASC));

        assertEquals(List.of("AAA111"), result.items().stream()
                .map(v -> v.licensePlate().value()).toList());
    }

    @Test
    void shouldExcludeDecommissionedVehiclesByDefault() {
        repository.items.add(vehicle("AAA111", "Ford", "Mustang", 1L));
        Vehicle removed = vehicle("BBB222", "Toyota", "Corolla", 2L);
        removed.decommission();
        repository.items.add(removed);

        var result = search.handle(new VehicleSearch(
                0, 100, null,
                new VehicleFilter(null, null, null, null),
                VehicleSortField.ID, SortDirection.ASC));

        assertEquals(List.of("AAA111"), result.items().stream()
                .map(v -> v.licensePlate().value()).toList());
    }

    @Test
    void shouldPaginateAfterFilteringAndSorting() {
        repository.items.add(vehicle("CCC333", "Ford", "C", 3L));
        repository.items.add(vehicle("AAA111", "Ford", "A", 1L));
        repository.items.add(vehicle("BBB222", "Ford", "B", 2L));

        var result = search.handle(new VehicleSearch(
                1, 1, null,
                new VehicleFilter("Ford", null, null, null),
                VehicleSortField.LICENSE_PLATE, SortDirection.ASC));

        assertEquals(List.of("BBB222"), result.items().stream()
                .map(v -> v.licensePlate().value()).toList());
        assertEquals(3, result.total());
        assertEquals(1, result.offset());
        assertEquals(1, result.limit());
        assertEquals(true, result.hasNextPage());
    }

    private Vehicle vehicle(String plate, String manufacturer, String model, long id) {
        return Vehicle.rehydrate(
                new org.acme.inventory.domain.model.VehicleId(id),
                new LicensePlate(plate),
                new VehicleSpecifications(
                        manufacturer, model, VehicleCategory.SUV,
                        Transmission.AUTOMATIC, null, 2025, "black", 5),
                null,
                VehicleStatus.AVAILABLE,
                null,
                null,
                null);
    }

    static class FakeVehicleRepository implements VehicleRepository {
        final List<Vehicle> items = new ArrayList<>();

        public List<Vehicle> findAll() { return List.copyOf(items); }
        public Optional<Vehicle> findByLicensePlate(LicensePlate plate) {
            return items.stream().filter(v -> v.licensePlate().equals(plate)).findFirst();
        }
        public Vehicle save(Vehicle vehicle) { return vehicle; }
    }
}
