package org.acme.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Description;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Description("Envelope genérico para respostas paginadas")
public class Page<T> {

    @Description("Itens da página atual")
    private List<T> items;

    @Description("Total de itens disponíveis no inventário")
    private long total;

    @Description("Índice do primeiro item desta página")
    private int offset;

    @Description("Quantidade máxima de itens retornados por página")
    private int limit;

    @Description("Indica se existe uma próxima página disponível")
    private boolean hasNextPage;
}