package org.acme.inventory.model.graphql;

import org.eclipse.microprofile.graphql.Description;

@Description("Campos aceitos para ordenação de veículos")
public enum CarSortField {

    @Description("Ordena por identificador")
    ID,

    @Description("Ordena por placa")
    PLATE_NUMBER,

    @Description("Ordena por fabricante")
    MANUFACTURER,

    @Description("Ordena por modelo")
    MODEL
}