package org.acme.users.adapter.in.web;

import jakarta.ws.rs.core.HttpHeaders;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import org.acme.users.adapter.in.web.templates.ReservationsTemplates;
import org.acme.users.application.exception.AvailabilityUnavailable;
import org.jboss.resteasy.reactive.server.ServerExceptionMapper;

/**
 * Mapeia o sinal de aplicacao {@link AvailabilityUnavailable} para 503 com o fragmento
 * explicativo que substitui a lista de carros.
 *
 * <p>Mapeamento puramente sintatico: a decisao de degradar ja foi tomada na fronteira de saida, e
 * este adapter so traduz o sinal para o transporte (regra 10 do AGENTS.md). 503 (e nao 200 com
 * lista vazia) porque a lista vazia mentiria "nenhum veiculo". O corpo e um fragmento HTML - o
 * {@code index.html} registra um {@code htmx:beforeSwap} para trocar respostas 503, senao o htmx
 * nao atualizaria o alvo. A causa entra no log do adapter de saida, nunca no corpo.
 */
@Provider
public class AvailabilityUnavailableMapper {

    /**
     * O outro lado pode gastar ate ~9s no pior caso (retry de leitura do inventory, ADR 009);
     * 30s e uma promessa conservadora de "tente de novo depois", nao uma medicao.
     */
    private static final int RETRY_AFTER_SECONDS = 30;

    @ServerExceptionMapper
    public Response toResponse(AvailabilityUnavailable unavailable) {
        return Response.status(Response.Status.SERVICE_UNAVAILABLE)
                .header(HttpHeaders.RETRY_AFTER, RETRY_AFTER_SECONDS)
                .type(MediaType.TEXT_HTML_TYPE)
                .entity(ReservationsTemplates.availabilityunavailable())
                .build();
    }
}
