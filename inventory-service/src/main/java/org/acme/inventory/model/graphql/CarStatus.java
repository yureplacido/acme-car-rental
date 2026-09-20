package org.acme.inventory.model.graphql;

import org.eclipse.microprofile.graphql.Description;

/**
 * Ciclo de vida do veículo na frota. O inventário é dono do status; a baixa
 * (soft delete) nunca apaga a linha, pois reservas podem referenciar o id.
 */
@Description("Situação de ciclo de vida do veículo na frota")
public enum CarStatus {

    @Description("Disponível para reserva")
    AVAILABLE,

    @Description("Em manutenção (fora da oferta quando filtrado)")
    IN_MAINTENANCE,

    @Description("Baixado da frota (soft delete; não volta à oferta)")
    DECOMMISSIONED
}