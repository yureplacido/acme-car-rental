package org.acme.reservation.adapter.out.inventory;

import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.ws.rs.ClientErrorException;
import jakarta.ws.rs.ProcessingException;
import jakarta.ws.rs.ServerErrorException;
import org.acme.reservation.application.exception.InventoryUnavailable;
import org.acme.reservation.application.port.out.InventoryGateway;
import org.acme.reservation.application.query.AvailableVehicle;
import org.acme.reservation.adapter.out.inventory.model.Car;
import org.eclipse.microprofile.faulttolerance.Fallback;
import org.eclipse.microprofile.faulttolerance.Retry;
import org.eclipse.microprofile.faulttolerance.Timeout;
import org.eclipse.microprofile.faulttolerance.exceptions.TimeoutException;
import org.jboss.logging.Logger;

import java.time.temporal.ChronoUnit;
import java.util.List;

/**
 * Politica de fault tolerance da leitura de disponibilidade (Cap.10 item 8).
 *
 * <p>Timeout, retry so para falha transitoria e fallback que <b>sinaliza</b> indisponibilidade.
 * Deliberadamente nao ha "degradacao": devolver lista vazia significaria "nenhum veiculo
 * disponivel", que e falso quando o inventory esta fora. A politica mora no adapter de saida
 * (decisao em docs/adr/009-fault-tolerance-chamadas-externas.md).
 *
 * <p>Falha transitoria = deadline estourado, erro de I/O do client ou resposta 5xx do inventory -
 * a mesma lista aparece em {@code @Retry.retryOn} e em {@code @Fallback.applyOn}: o que pode ser
 * repetido e o que pode virar "indisponivel". Erro de determinismo (consulta invalida, 4xx,
 * falha de mapeamento) fica fora dos dois: repetir nao muda o resultado e nao e indisponibilidade.
 */
@ApplicationScoped
public class GraphQLInventoryGateway implements InventoryGateway {

    private static final Logger LOG = Logger.getLogger(GraphQLInventoryGateway.class);

    private final GraphQLInventoryClient client;

    @Inject
    public GraphQLInventoryGateway(GraphQLInventoryClient client) {
        this.client = client;
    }

    @Override
    @Timeout(value = 2, unit = ChronoUnit.SECONDS)
    @Retry(
            maxRetries = 2,
            delay = 200,
            delayUnit = ChronoUnit.MILLIS,
            jitter = 100,
            jitterDelayUnit = ChronoUnit.MILLIS,
            retryOn = {TimeoutException.class, ProcessingException.class, ServerErrorException.class},
            abortOn = ClientErrorException.class)
    @Fallback(
            fallbackMethod = "inventoryUnreachable",
            applyOn = {TimeoutException.class, ProcessingException.class, ServerErrorException.class})
    public Uni<List<AvailableVehicle>> findVehicles() {
        return client.allCars()
                .map(cars -> cars.stream()
                        .map(this::toAvailableVehicle)
                        .toList());
    }

    /**
     * Fallback que traduz "nao foi possivel saber" em sinal, e nao em valor. Parametro extra de
     * excecao no final da assinatura: extensao do SmallRye FT disponivel no modo nao-compativel,
     * que e o default do Quarkus. Grava a causa para diagnostico; o adapter inbound decide o
     * que o cliente ve.
     */
    private Uni<List<AvailableVehicle>> inventoryUnreachable(Throwable cause) {
        LOG.warnf(cause, "Inventory did not answer the vehicle catalogue; availability is unknown");
        return Uni.createFrom().failure(new InventoryUnavailable(cause));
    }

    private AvailableVehicle toAvailableVehicle(Car car) {
        return new AvailableVehicle(
                car.getId(),
                car.getLicensePlateNumber(),
                car.getManufacturer(),
                car.getModel());
    }
}
