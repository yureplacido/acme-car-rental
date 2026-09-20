package org.acme.reservation.adapter.out.inventory;

import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.graphql.Query;
import org.acme.reservation.adapter.out.inventory.model.Car;

import java.util.List;

@GraphQLClientApi(configKey = "inventory")
public interface GraphQLInventoryClient {
    @Query("allCars")
    Uni<List<Car>> allCars();
}
