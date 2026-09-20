package org.acme.inventory.model.graphql;

import org.eclipse.microprofile.graphql.Description;

@Description("Direção da ordenação")
public enum SortOrder {

    @Description("Ordem crescente")
    ASC,

    @Description("Ordem decrescente")
    DESC
}