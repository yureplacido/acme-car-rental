package org.acme.reservation.adapter.out.inventory;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.InputObject;
import io.smallrye.graphql.client.core.InputObjectField;
import io.smallrye.graphql.client.core.Argument;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.adapter.out.inventory.model.Car;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

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
    @io.smallrye.graphql.client.GraphQLClient("inventory")
    DynamicGraphQLClient client;

    @Override
    public List<Car> all() {
        return all(DEFAULT_FIELDS);
    }

    @Override
    public List<Car> all(Collection<String> fields) {
        Document document = document(operation(field("allCars", fields(project(fields)))));
        return execute(client, document).getList(Car.class, "allCars");
    }

    public Uni<List<Car>> allAsync(Collection<String> fields) {
        Document document = document(operation(field("allCars", fields(project(fields)))));
        return client.executeAsync(document)
                .map(response -> response.getList(Car.class, "allCars"));
    }

    @Override
    public List<Car> page(InventoryQuery query) {
        Document document = document(operation(
                field("allCars", carArgs(query), fields(project(query.fields())))));
        return execute(client, document).getList(Car.class, "allCars");
    }

    public CarPage carPage(InventoryQuery query) {
        Document document = document(operation(
                field("allCarsPage", carArgs(query),
                        field("items", fields(project(query.fields()))),
                        field("total"), field("offset"), field("limit"), field("hasNextPage"))));
        return execute(client, document).getObject(CarPage.class, "allCarsPage");
    }

    @Override
    public List<String> getDefaultFields() {
        return DEFAULT_FIELDS;
    }

    private List<Argument> carArgs(InventoryQuery query) {
        List<Argument> args = new ArrayList<>();
        args.add(arg("offset", query.offset()));
        args.add(arg("limit", query.limit()));
        if (query.search() != null && !query.search().isBlank()) {
            args.add(arg("search", query.search().trim()));
        }
        if (hasFilter(query.filter())) {
            args.add(arg("filter", inputObject(filterFields(query.filter()))));
        }
        if (query.sort() != null) args.add(arg("sort", query.sort()));
        if (query.order() != null) args.add(arg("order", query.order()));
        return args;
    }

    private boolean hasFilter(CarFilter filter) {
        return filter != null && (isNotBlank(filter.manufacturer())
                || isNotBlank(filter.model()) || isNotBlank(filter.plate()));
    }

    private InputObjectField[] filterFields(CarFilter filter) {
        List<InputObjectField> fields = new ArrayList<>();
        if (isNotBlank(filter.manufacturer())) fields.add(prop("manufacturer", filter.manufacturer().trim()));
        if (isNotBlank(filter.model())) fields.add(prop("model", filter.model().trim()));
        if (isNotBlank(filter.plate())) fields.add(prop("plate", filter.plate().trim()));
        return fields.toArray(new InputObjectField[0]);
    }

    private boolean isNotBlank(String value) {
        return value != null && !value.isBlank();
    }

    private Response execute(DynamicGraphQLClient client, Document document) {
        try {
            return client.executeSync(document);
        } catch (ExecutionException e) {
            throw new IllegalStateException("GraphQL inventory query failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GraphQL inventory query interrupted", e);
        }
    }
}
