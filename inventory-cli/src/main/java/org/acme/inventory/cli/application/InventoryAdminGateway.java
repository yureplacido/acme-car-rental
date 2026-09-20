package org.acme.inventory.cli.application;

import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.acme.inventory.model.CarResponse;
import org.acme.inventory.model.InsertCarRequest;
import org.acme.inventory.model.RemoveCarRequest;

public interface InventoryAdminGateway {
    Multi<CarResponse> add(InsertCarRequest request);
    Uni<CarResponse> remove(RemoveCarRequest request);
}
