package org.acme.reservation.adapter.out.inventory;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.Field;
import io.smallrye.graphql.client.core.FieldOrFragment;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import org.acme.reservation.adapter.out.inventory.model.Car;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface InventoryClient<T> {
    List<T> all();
    List<T> all(Collection<String> fields);
    List<T> page(InventoryQuery query);
    List<String> getDefaultFields();

    default FieldOrFragment[] fields(Collection<String> fields) {
        return fields.stream().map(Field::field).toArray(FieldOrFragment[]::new);
    }

    default Response execute(DynamicGraphQLClient client, Document document) {
        try {
            return client.executeSync(document);
        } catch (ExecutionException e) {
            throw new IllegalStateException("GraphQL inventory query failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("GraphQL inventory query interrupted", e);
        }
    }

    default List<String> project(Collection<String> requested) {
        return requested == null || requested.isEmpty() ? getDefaultFields() : requested.stream().toList();
    }
}
