package org.acme.inventory.cli.adapter.out.grpc;

import io.quarkus.grpc.GrpcClient;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.inventory.cli.application.InventoryAdminGateway;
import org.acme.inventory.model.CarResponse;
import org.acme.inventory.model.InsertCarRequest;
import org.acme.inventory.model.InventoryService;
import org.acme.inventory.model.RemoveCarRequest;

@ApplicationScoped
public class InventoryGrpcGateway implements InventoryAdminGateway {

    @GrpcClient("inventory")
    InventoryService client;

    @Override
    public Multi<CarResponse> add(InsertCarRequest request) {
        return client.add(Multi.createFrom().item(request));
    }

    @Override
    public Uni<CarResponse> remove(RemoveCarRequest request) {
        return client.remove(request);
    }
}
