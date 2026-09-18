package org.acme.reservation.inventory;

import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

import java.util.Collection;
import java.util.List;

import static io.smallrye.graphql.client.core.Argument.arg;
import static io.smallrye.graphql.client.core.Argument.args;
import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;

@ApplicationScoped
public class DynamicInventoryClient implements InventoryClient<Car> {

    private static final List<String> DEFAULT_FIELDS = List.of("id", "plateNumber", "manufacturer", "model");

    @Inject
    @GraphQLClient("inventory")
    DynamicGraphQLClient client;

    @Override
    public List<Car> all() {
        Document cars = document(
                operation(
                        field("allCars", fields(DEFAULT_FIELDS))
                )
        );
        Response response = execute(client, cars);
        return response.getList(Car.class, "allCars");
    }

    @Override
    public List<Car> page(int offset, int limit, Collection<String> fields) {
        Document query = document(
                operation(
                        field("allCars",
                                args(arg("offset", offset), arg("limit", limit)),
                                fields(project(fields))
                        )
                )
        );
        Response response = execute(client, query);
        return response.getList(Car.class, "allCars");
    }

    @Override
    public List<String> getDefaultFields() {
        return DEFAULT_FIELDS;
    }

    public CarPage carPage(int offset, int limit, Collection<String> fields) {
        Document query = document(
                operation(
                        field("allCarsPage",
                                args(arg("offset", offset), arg("limit", limit)),
                                field("items", fields(project(fields))),
                                field("total"),
                                field("offset"),
                                field("limit"),
                                field("hasNextPage")
                        )
                )
        );
        Response response = execute(client, query);
        return response.getObject(CarPage.class, "allCarsPage");
    }

}