package org.acme.inventory.model.graphql;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.eclipse.microprofile.graphql.Description;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Description("Filtros estruturados para a consulta de veículos")
public class CarFilter {

    @Description("Filtra por fabricante/marca (ex.: Ford)")
    private String manufacturer;

    @Description("Filtra por modelo (ex.: Mustang)")
    private String model;

    @Description("Filtra por placa (ex.: XYZ987)")
    private String plate;

    @Description("Filtra por status do ciclo de vida. Sem filtro, veículos baixados (DECOMMISSIONED) ficam fora da resposta")
    private CarStatus status;
}