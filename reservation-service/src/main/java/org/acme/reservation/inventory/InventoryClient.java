package org.acme.reservation.inventory;

import io.smallrye.graphql.client.Response;
import io.smallrye.graphql.client.core.Document;
import io.smallrye.graphql.client.core.Field;
import io.smallrye.graphql.client.core.FieldOrFragment;
import io.smallrye.graphql.client.dynamic.api.DynamicGraphQLClient;
import lombok.NonNull;

import java.util.Collection;
import java.util.List;
import java.util.concurrent.ExecutionException;

public interface InventoryClient<T> {

    List<T> all();

    List<T> all(Collection<String> fields);

    List<T> page(InventoryQuery query);

    List<String> getDefaultFields();

    default FieldOrFragment[] fields(@NonNull Collection<String> fields) {
        return fields.stream()
                .map(Field::field)
                .toArray(FieldOrFragment[]::new);
    }

    default Response execute(@NonNull DynamicGraphQLClient client, @NonNull Document document) {
        try {
            return client.executeSync(document);
        } catch (ExecutionException | InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Falha ao consultar o inventário via GraphQL dinâmico", e);
        }
    }

    default List<String> project(Collection<String> requested) {
        if (requested == null || requested.isEmpty()) {
            return getDefaultFields();
        }
        return requested.stream().toList();
    }
}
