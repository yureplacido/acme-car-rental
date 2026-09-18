package org.acme.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Description;

import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Description("Envelope genérico para respostas paginadas")
public class Page<T> {

    public static final int MAX_LIMIT = 100;

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

    public static <T> Page<T> of(List<T> source, int offset, int limit) {
        return of(source, offset, limit, t -> true, (a, b) -> 0);
    }

    public static <T> Page<T> of(List<T> source, int offset, int limit,
                                 Predicate<T> filter, Comparator<T> sort) {
        List<T> matching = source.stream()
                .filter(filter)
                .sorted(sort)
                .toList();
        int from = Math.max(0, offset);
        int size = Math.clamp(limit, 0, MAX_LIMIT);
        List<T> items = matching.stream()
                .skip(from)
                .limit(size)
                .toList();

        return Page.<T>builder()
                .items(items)
                .total(matching.size())
                .offset(from)
                .limit(size)
                .hasNextPage(from + size < matching.size())
                .build();
    }

}