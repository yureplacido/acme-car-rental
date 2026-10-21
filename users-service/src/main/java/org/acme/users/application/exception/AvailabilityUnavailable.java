package org.acme.users.application.exception;

/**
 * Sinal de aplicacao: a porta de saida {@code ReservationsGateway} nao conseguiu responder a
 * disponibilidade.
 *
 * <p>Nao e lista vazia: lista vazia significa "nenhum veiculo disponivel" para o periodo; esta
 * excecao significa "nao foi possivel saber". O adapter de saida (que mede a taxonomia do cliente
 * REST) e o adapter inbound (que decide a resposta HTTP) sao os unicos lugares que nomeiam este
 * tipo.
 *
 * <p>A mensagem e estavel: a causa entra so para diagnostico, nunca no corpo da resposta.
 */
public final class AvailabilityUnavailable extends RuntimeException {

    private static final String MESSAGE = "reservation availability is unavailable";

    public AvailabilityUnavailable(Throwable cause) {
        super(MESSAGE, cause);
    }
}
