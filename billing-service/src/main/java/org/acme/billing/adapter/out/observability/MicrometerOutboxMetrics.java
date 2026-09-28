package org.acme.billing.adapter.out.observability;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.acme.billing.application.port.out.OutboxMetrics;
import org.jboss.logging.Logger;

import java.util.concurrent.atomic.AtomicLong;

/**
 * Métricas do relay da outbox expostas via Micrometer.
 *
 * <p>A atualização do gauge de backlog é sincronizada por um job agendado
 * (agendamento é infraestrutura de adapter). O método agendado retorna
 * {@code Uni<Void>} para que {@code concurrentExecution = SKIP} valha de fato:
 * com assinatura {@code void} o scheduler consideraria a invocação concluída
 * no retorno imediato e permitiria consultas sobrepostas (regra 14 do AGENTS.md).</p>
 */
@ApplicationScoped
public class MicrometerOutboxMetrics implements OutboxMetrics {

    private static final Logger LOG =
            Logger.getLogger(MicrometerOutboxMetrics.class);

    private final Counter relayed;
    private final Counter failures;
    private final Counter backlogRefreshErrors;
    private final AtomicLong pendingBacklog;
    private final OutboxEventStore outboxEventStore;

    public MicrometerOutboxMetrics(
            MeterRegistry registry,
            OutboxEventStore outboxEventStore) {
        this.relayed = Counter.builder("billing.outbox.published")
                .description("Outbox events successfully relayed to Kafka")
                .register(registry);
        this.failures = Counter.builder("billing.outbox.failures")
                .description("Outbox events that failed to be relayed")
                .register(registry);
        this.backlogRefreshErrors = Counter.builder("billing.outbox.backlog.refresh.errors")
                .description("Failed attempts to measure the outbox backlog")
                .register(registry);
        this.pendingBacklog = registry.gauge(
                "billing.outbox.pending",
                new AtomicLong(0),
                AtomicLong::get);
        this.outboxEventStore = outboxEventStore;
    }

    @Override
    public void eventRelayed() {
        relayed.increment();
    }

    @Override
    public void relayFailed() {
        failures.increment();
    }

    @Scheduled(
            every = "5s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> refreshBacklog() {
        return outboxEventStore.countPending()
                .invoke(pendingBacklog::set)
                .onFailure()
                .invoke(error -> {
                    backlogRefreshErrors.increment();
                    LOG.warnf(error, "Could not refresh the outbox backlog gauge");
                })
                .replaceWithVoid();
    }
}