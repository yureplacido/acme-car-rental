package org.acme.reservation.adapter.in.rest.model;

/**
 * Corpo do 503 de indisponibilidade do catalogo. Vive em {@code adapter/in/rest/model} como os
 * demais DTOs de transporte do servico: o contrato e do adapter de entrada, nao da aplicacao.
 */
public record InventoryUnavailableResponse(
        String code,
        String message,
        int retryAfterSeconds) {
}
