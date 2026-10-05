package org.acme.reservation.application.exception;

/**
 * Sinal de aplicacao: a porta de saida {@code InventoryGateway} nao conseguiu responder.
 *
 * <p>Nao e violacao de invariante do agregado {@code Reservation} - a reserva continua valida.
 * E a falha de um colaborador: a disponibilidade de um periodo e derivada combinando o
 * catalogo do Inventory com os conflitos de reserva, e sem o catalogo essa resposta nao
 * existe. Por isso a falha atravessa a porta como {@code Uni} falho em vez de virar valor.
 *
 * <p>Distincao obrigatoria: lista vazia significa "nenhum veiculo disponivel"; esta excecao
 * significa "nao foi possivel saber". O adapter de saida (politica de fault tolerance) e o
 * adapter inbound (resposta HTTP) sao os unicos lugares que nomeiam este tipo.
 *
 * <p>A mensagem e estavel: a causa entra so para diagnostico, nunca no corpo da resposta.
 */
public final class InventoryUnavailable extends RuntimeException {

    private static final String MESSAGE = "inventory is unavailable";

    public InventoryUnavailable(Throwable cause) {
        super(MESSAGE, cause);
    }
}
