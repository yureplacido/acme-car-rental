package org.acme.inventory.application.usecase;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.inventory.application.port.out.VehicleRepository;
import org.acme.inventory.application.query.SortDirection;
import org.acme.inventory.application.query.VehicleFilter;
import org.acme.inventory.application.query.VehiclePage;
import org.acme.inventory.application.query.VehicleSearch;
import org.acme.inventory.application.query.VehicleSortField;
import org.acme.inventory.domain.model.Vehicle;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

@ApplicationScoped
public class SearchVehicles {

    private static final int MAX_LIMIT = 100;

    private final VehicleRepository repository;

    @Inject
    public SearchVehicles(VehicleRepository repository) {
        this.repository = repository;
    }

    public VehiclePage handle(VehicleSearch search) {
        int limit = Math.min(search.limit(), MAX_LIMIT);

        List<Vehicle> matching = repository.findAll().stream()
                .filter(matches(search.search(), search.filter()))
                .sorted(comparator(search.sort(), search.direction()))
                .toList();

        List<Vehicle> items = matching.stream()
                .skip(search.offset())
                .limit(limit)
                .toList();

        return new VehiclePage(
                items,
                matching.size(),
                search.offset(),
                limit,
                search.offset() + limit < matching.size());
    }

    private Predicate<Vehicle> matches(String search, VehicleFilter filter) {
        String term = search == null ? null : search.toLowerCase(Locale.ROOT);

        return vehicle -> {
            if (term != null && !containsText(vehicle, term)) {
                return false;
            }

            if (filter == null) {
                return vehicle.canBeOffered();
            }

            if (!matchesText(filter.manufacturer(), vehicle.specifications().manufacturer())
                    || !matchesText(filter.model(), vehicle.specifications().model())
                    || !matchesText(filter.plate(), vehicle.licensePlate().value())) {
                return false;
            }

            if (filter.status() != null) {
                return vehicle.status() == filter.status();
            }

            return vehicle.canBeOffered();
        };
    }

    private boolean containsText(Vehicle vehicle, String term) {
        return vehicle.licensePlate().value().toLowerCase(Locale.ROOT).contains(term)
                || vehicle.specifications().manufacturer().toLowerCase(Locale.ROOT).contains(term)
                || vehicle.specifications().model().toLowerCase(Locale.ROOT).contains(term);
    }

    private boolean matchesText(String expected, String actual) {
        return expected == null || expected.isBlank()
                || actual.equalsIgnoreCase(expected.trim());
    }

    private Comparator<Vehicle> comparator(VehicleSortField sort, SortDirection direction) {
        Comparator<Vehicle> comparator = switch (sort == null ? VehicleSortField.ID : sort) {
            case LICENSE_PLATE -> Comparator.comparing(v -> v.licensePlate().value());
            case MANUFACTURER -> Comparator.comparing(v -> v.specifications().manufacturer());
            case MODEL -> Comparator.comparing(v -> v.specifications().model());
            case ID -> Comparator.comparing(v -> v.id().value());
        };

        return direction == SortDirection.DESC ? comparator.reversed() : comparator;
    }
}
