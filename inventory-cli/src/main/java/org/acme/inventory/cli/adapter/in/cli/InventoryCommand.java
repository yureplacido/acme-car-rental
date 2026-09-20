package org.acme.inventory.cli.adapter.in.cli;

import io.quarkus.runtime.QuarkusApplication;
import io.quarkus.runtime.annotations.QuarkusMain;
import jakarta.inject.Inject;
import org.acme.inventory.cli.application.InventoryAdminGateway;
import org.acme.inventory.model.InsertCarRequest;
import org.acme.inventory.model.RemoveCarRequest;

@QuarkusMain
public class InventoryCommand implements QuarkusApplication {

    private static final String USAGE =
            "Usage: inventory <add>|<remove> <license plate number> [<manufacturer> <model>]";

    @Inject
    InventoryAdminGateway inventory;

    @Override
    public int run(String... args) {
        String action = args.length > 0 ? args[0] : null;

        if ("add".equals(action) && args.length >= 4) {
            add(args[1], args[2], args[3]);
            return 0;
        }
        if ("remove".equals(action) && args.length >= 2) {
            remove(args[1]);
            return 0;
        }

        System.err.println(USAGE);
        return 1;
    }

    private void add(String plate, String manufacturer, String model) {
        inventory.add(InsertCarRequest.newBuilder()
                        .setLicensePlateNumber(plate)
                        .setManufacturer(manufacturer)
                        .setModel(model)
                        .build())
                .collect().asList()
                .await().indefinitely()
                .forEach(response -> System.out.println("Inserted new car " + response));
    }

    private void remove(String plate) {
        inventory.remove(RemoveCarRequest.newBuilder()
                        .setLicensePlateNumber(plate)
                        .build())
                .onItem().invoke(response ->
                        System.out.println(response == null || response.getLicensePlateNumber().isEmpty()
                                ? "No car found with plate " + plate
                                : "Removed car " + response))
                .await().indefinitely();
    }
}
