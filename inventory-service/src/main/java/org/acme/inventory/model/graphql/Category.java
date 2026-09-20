package org.acme.inventory.model.graphql;

import org.eclipse.microprofile.graphql.Description;

/** Segmento/comercial do veículo, base para o pricing do billing. */
@Description("Segmento comercial do veículo")
public enum Category {

    ECONOMY,
    COMPACT,
    MIDSIZE,
    SUV,
    LUXURY
}