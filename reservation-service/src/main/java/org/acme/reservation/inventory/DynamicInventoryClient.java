package org.acme.reservation.inventory;

import io.smallrye.graphql.client.GraphQLClient;
import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import lombok.RequiredArgsConstructor;

import java.util.List;
import java.util.concurrent.ExecutionException;

import static io.smallrye.graphql.client.core.Document.document;
import static io.smallrye.graphql.client.core.Field.field;
import static io.smallrye.graphql.client.core.Operation.operation;

@ApplicationScoped
@RequiredArgsConstructor
public class DynamicInventoryClient implements InventoryClient {

    @Inject
    @GraphQLClient("inventory")
    final DynamicGraphQLClient client;

    @Override
    public List<Car> allCars() {
        Document cars = document(
                operation(
                        field("allCars",
                                field("id"),
                                field("plateNumber"),
                                field("manufacturer"),
                                field("model")
                        )
                )
        );
        try {
            Response response = client.executeSync(cars);
            return response.getList(Car.class, "allCars");
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao consultar o inventário via GraphQL dinâmico", e);
        }
    }
}