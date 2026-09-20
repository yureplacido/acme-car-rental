package org.acme.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;

/**
 * Modelo de domínio do veículo — POJO puro (sem JPA). É o tipo público do GraphQL
 * e do gRPC; a persistência vive em {@code CarEntity} (Panache), mapeado por
 * {@code CarMapper}. Assim o Active Record ou o Repository pattern podem mudar
 * sem tocar no modelo e no contrato GraphQL.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Description("Representa um veículo cadastrado no inventário da frota")
public class Car {

    @Id
    @NonNull
    @Description("Identificador único e incremental do veículo")
    private Long id;

    @NonNull
    @Description("Marca ou fabricante do veículo (ex: Ford, Mazda)")
    private String manufacturer;

    @NonNull
    @Description("Modelo específico do carro (ex: Mustang, 6)")
    private String model;

    @NonNull
    @Name("plateNumber") // Expõe o campo para o cliente GraphQL como 'plateNumber'
    @Description("Placa de identificação única do automóvel")
    private String licensePlateNumber;
}