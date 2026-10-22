package org.acme.reservation.adapter.out.rental;

import io.quarkus.test.junit.QuarkusTestProfile;

import java.util.Map;

/**
 * Mutacao versionada do prazo de transporte: sobe o {@code read-timeout} do cliente de
 * escrita para <b>acima</b> do deadline de fault tolerance (2 s).
 *
 * <p>Com o prazo de transporte abaixo do deadline de FT, e o transporte que corta a chamada em
 * voo — e o peer ve o socket fechar ({@link RentalTransportAbortTest}). Com o prazo acima, a
 * ordem se inverte: o deadline de FT vence primeiro, entrega {@code TimeoutException} ao
 * chamador e <b>deixa a chamada viva</b> — o peer ve o socket ainda aberto. E a segunda
 * metade da ADR 009 §4, provada com peer real em vez de com cliente mockado.
 *
 * <p>A mutacao e um perfil de teste, e nao uma edicao do {@code application.properties}: ela
 * fica versionada, entao a forca do teste do caminho de producao e verificada pelo build a
 * cada execucao — em vez de depender de um experimento manual, revertido depois.
 */
public class TransportTimeoutAboveDeadlineProfile implements QuarkusTestProfile {

    /** O prazo de transporte com a mutacao aplicada, em milissegundos. */
    static final long READ_TIMEOUT_MILLIS = 5_000;

    @Override
    public Map<String, String> getConfigOverrides() {
        return Map.of(
                "quarkus.rest-client.\"org.acme.reservation.adapter.out.rental.RentalClient\".read-timeout",
                String.valueOf(READ_TIMEOUT_MILLIS));
    }
}
