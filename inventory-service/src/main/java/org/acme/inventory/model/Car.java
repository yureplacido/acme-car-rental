package org.acme.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Description("Representa um veículo cadastrado no inventário da frota")
public class Car {

    @Id // Define explicitamente como o tipo escalar 'ID' do GraphQL
    @NonNull // Torna o campo obrigatório no output do Schema (ID!)
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
