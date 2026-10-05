package org.acme.reservation.adapter.in.rest;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.acme.reservation.adapter.in.rest.model.InventoryUnavailableResponse;
import org.acme.reservation.application.exception.InventoryUnavailable;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Mapeia o sinal de aplicacao {@link InventoryUnavailable} para 503 com corpo estavel.
 *
 * <p>Mapeamento puramente sintatico: a decisao de degradar ou nao ja foi tomada na fronteira de
 * saida, e este adapter so traduz o sinal para o transporte (regra 10 do AGENTS.md). A causa entra
 * no log do adapter de saida, nunca no corpo - o corpo e contrato.
 */
@Provider
public class InventoryUnavailableMapper {

    /**
     * Pior caso da leitura: 3 idas com o deadline de leitura
     * ({@code GraphQLInventoryGateway.READ_DEADLINE_MILLIS}) + backoff. O 30s e uma promessa
     * conservadora de "tente de novo depois", nao uma medicao.
     */
    private static final int RETRY_AFTER_SECONDS = 30;

    @ServerExceptionMapper
    public Response toResponse(InventoryUnavailable unavailable) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .type(MediaType.APPLICATION_JSON_TYPE)
                .entity(new InventoryUnavailableResponse(
                        "INVENTORY_UNAVAILABLE",
                        "vehicle inventory is temporarily unavailable",
                        RETRY_AFTER_SECONDS))
                .build();
    }
}
