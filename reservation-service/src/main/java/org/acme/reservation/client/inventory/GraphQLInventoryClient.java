package org.acme.reservation.client.inventory;

import io.smallrye.graphql.client.typesafe.api.GraphQLClientApi;
import io.smallrye.mutiny.Uni;
import org.eclipse.microprofile.graphql.Query;

import java.util.List;

@GraphQLClientApi(configKey = "inventory")
public interface GraphQLInventoryClient {

    /**
     * Livro 7.7: a busca de carros passou a retornar {@link Uni} para não bloquear o
     * event loop no fluxo reativo do ReservationResource.
     */
    @Query("allCars")
    Uni<List<Car>> allCars();
}