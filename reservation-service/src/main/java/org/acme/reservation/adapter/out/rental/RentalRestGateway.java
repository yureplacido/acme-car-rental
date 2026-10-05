package org.acme.reservation.adapter.out.rental;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import org.acme.reservation.application.port.out.RentalGateway;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.rest.client.inject.RestClient;

import java.time.temporal.ChronoUnit;

/**
 * Politica de fault tolerance da escrita de locacao (Cap.10 item 8): <b>somente</b> timeout.
 *
 * <p>Sem {@code @Retry}: o rental-service sempre salva uma nova locacao em {@code StartRental},
 * sem consultar {@code findByCustomerAndReservation} - a operacao nao e idempotente, entao
 * repetir depois de um resultado incerto criaria locacao duplicada para a mesma reserva.
 *
 * <p>Sem {@code @Fallback}: engolir a falha devolveria sucesso a criacao da reserva sem locacao
 * registrada - o cliente acharia que a locacao comecou.
 *
 * <p>Sobre o deadline e o cancelamento: o {@code @Timeout} do SmallRye Fault Tolerance em metodo
 * que devolve {@code Uni} emite {@code TimeoutException} para o chamador mas <b>nao cancela</b> a
 * subscription a montante (verificado com emitter que so termina por cancelamento). Entao o prazo
 * do transporte tambem e configurado, sempre menor que este deadline, para que a chamada em voo
 * seja abortada de verdade. {@link #WRITE_DEADLINE_MILLIS} fica publico para o teste de guarda
 * conferir essa relacao.
 *
 * <p>Decisao e evidencia em docs/adr/009-fault-tolerance-chamadas-externas.md.
 */
@ApplicationScoped
public class RentalRestGateway implements RentalGateway {

    /** Deadline da escrita, em um unico lugar: annotation, guarda do teste e ADR. */
    public static final long WRITE_DEADLINE_MILLIS = 2_000;

    private final RentalClient client;

    @Inject
    public RentalRestGateway(@RestClient RentalClient client) {
        this.client = client;
    }

    @Override
    @Timeout(value = WRITE_DEADLINE_MILLIS, unit = ChronoUnit.MILLIS)
    public Uni<Void> start(String customerId, Long reservationId) {
        return client.start(customerId, reservationId).replaceWithVoid();
    }
}
