package org.acme.inventory.model.graphql;

import org.eclipse.microprofile.graphql.Description;

/** Tipo de combustível do veículo. */
@Description("Tipo de combustível do veículo")
public enum FuelType {

    GASOLINE,
    DIESEL,
    ELECTRIC,
    HYBRID
}