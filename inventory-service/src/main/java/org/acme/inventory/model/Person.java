package org.acme.inventory.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Description;
import org.eclipse.microprofile.graphql.Id;
import org.eclipse.microprofile.graphql.Name;
import org.eclipse.microprofile.graphql.NonNull;
import org.eclipse.microprofile.graphql.Type;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@Type("UserAccount") // Altera o nome do tipo que aparecerá exposto no Schema do GraphQL (padrão seria 'Person')
public class Person {

    @Id // Mapeia o campo explicitamente como o tipo escalar 'ID' do GraphQL (essencial para chaves primárias)
    @NonNull // Torna o campo obrigatório no Schema (ID! no GraphQL)
    @Description("Identificador único e imutável da pessoa")
    private String id;

    @NonNull // Nome passa a ser obrigatório no Input e no Output (String!)
    @Name("fullName") // Altera o nome da propriedade exposta apenas no GraphQL para 'fullName'
    private String name;

    // @Ignore // Se descomentado, este campo sumiria completamente da API GraphQL, ficando invisível ao cliente
    @Description("Idade em anos da pessoa cadastrada")
    private Integer age;

    @Description("ID do carro associado à pessoa")
    private Long carId; // <-- Nova propriedade de ligação (ID do carro da pessoa)
}