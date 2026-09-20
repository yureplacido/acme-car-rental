package org.acme.reservation.client.inventory;

import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.InputObject;
import io.smallrye.graphql.client.core.InputObjectField;
import io.smallrye.graphql.client.core.Argument;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.InputObject.inputObject;
import static io.smallrye.graphql.client.core.InputObjectField.prop;
import static io.smallrye.graphql.client.core.Operation.operation;

@ApplicationScoped
public class DynamicInventoryClient implements InventoryClient<Car> {

    private static final List<String> DEFAULT_FIELDS = List.of("id", "plateNumber", "manufacturer", "model");

    @Inject
    @GraphQLClient("inventory")
    DynamicGraphQLClient client;

    @Override
    public List<Car> all() {
        return all(DEFAULT_FIELDS);
    }

    @Override
    public List<Car> all(Collection<String> fields) {
        Document cars = document(
                operation(
                        field("allCars", fields(project(fields)))
                )
        );
        Response response = execute(client, cars);
        return response.getList(Car.class, "allCars");
    }

    /**
     * Livro 7.7: variante não-bloqueante do {@link #all(Collection)} usada pelo
     * {@code availability/dynamic} no fluxo reativo do ReservationResource.
     */
    public Uni<List<Car>> allAsync(Collection<String> fields) {
        Document cars = document(
                operation(
                        field("allCars", fields(project(fields)))
                )
        );
        return client.executeAsync(cars)
                .map(response -> response.getList(Car.class, "allCars"));
    }

    @Override
    public List<Car> page(InventoryQuery query) {
        Document document = document(
                operation(
                        field("allCars",
                                carArgs(query),
                                fields(project(query.fields()))
                        )
                )
        );
        Response response = execute(client, document);
        return response.getList(Car.class, "allCars");
    }

    @Override
    public List<String> getDefaultFields() {
        return DEFAULT_FIELDS;
    }

    public CarPage carPage(InventoryQuery query) {
        Document document = document(
                operation(
                        field("allCarsPage",
                                carArgs(query),
                                field("items", fields(project(query.fields()))),
                                field("total"),
                                field("offset"),
                                field("limit"),
                                field("hasNextPage")
                        )
                )
        );
        Response response = execute(client, document);
        return response.getObject(CarPage.class, "allCarsPage");
    }

    private List<Argument> carArgs(InventoryQuery query) {
        List<Argument> args = new ArrayList<>();
        args.add(arg("offset", query.offset()));
        args.add(arg("limit", query.limit()));
        if (query.search() != null && !query.search().isBlank()) {
            args.add(arg("search", query.search().trim()));
        }
        if (hasFilter(query.filter())) {
            InputObject object = inputObject(filterFields(query.filter()));
            args.add(arg("filter", object));
        }
        if (query.sort() != null) {
            args.add(arg("sort", query.sort()));
        }
        if (query.order() != null) {
            args.add(arg("order", query.order()));
        }
        return args;
    }

    private boolean hasFilter(CarFilter filter) {
        return filter != null
                && (isNotBlank(filter.manufacturer()) || isNotBlank(filter.model()) || isNotBlank(filter.plate()));
    }

    private InputObjectField[] filterFields(CarFilter filter) {
        List<InputObjectField> fields = new ArrayList<>();
        if (isNotBlank(filter.manufacturer())) {
            fields.add(prop("manufacturer", filter.manufacturer().trim()));
        }
        if (isNotBlank(filter.model())) {
            fields.add(prop("model", filter.model().trim()));
        }
        if (isNotBlank(filter.plate())) {
            fields.add(prop("plate", filter.plate().trim()));
        }
        return fields.toArray(new InputObjectField[0]);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

}