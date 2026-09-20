package org.acme.inventory.model.graphql;

import org.eclipse.microprofile.graphql.Description;

/** Tipo de câmbio do veículo. */
@Description("Tipo de câmbio do veículo")
public enum Transmission {

    AUTOMATIC,
    MANUAL
}