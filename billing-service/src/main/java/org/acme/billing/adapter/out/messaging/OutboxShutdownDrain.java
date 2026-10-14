package org.acme.billing.adapter.out.messaging;

import io.quarkus.runtime.ShutdownEvent;
import io.quarkus.vertx.core.runtime.context.VertxContextSafetyToggle;
import io.smallrye.common.vertx.VertxContext;
import io.smallrye.mutiny.subscription.Cancellable;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import jakarta.inject.Inject;
import org.acme.billing.application.usecase.PublishPendingOutboxEvents;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Drena a outbox no shutdown.
 *
 * <p>O graceful shutdown do Quarkus so aguarda requisicoes HTTP em voo ({@code quarkus.shutdown.timeout});
 * trabalho agendado — como o {@link OutboxRelay} — e' cortado sem publicar o lote em andamento.
 * Entao, no {@link io.quarkus.runtime.ShutdownEvent}, fazemos uma drenagem final bounded.
 *
 * <p>O observador de {@code ShutdownEvent} roda na thread de shutdown (uma thread comum, sem contexto
 * Vert.x), mas o {@link PublishPendingOutboxEvents} usa Hibernate Reactive/Panache ({@code @WithSession}),
 * que exige um contexto Vert.x duplicado e marcado como seguro. Por isso a drenagem e' despachada para
 * um novo contexto duplicado e a thread de shutdown aguarda, no maximo, {@code acme.billing.outbox.shutdown-timeout}.
 * O datasource ainda esta aberto nesse ponto: o Quarkus registra no {@code ShutdownContext} a
 * tarefa que fecha o pool reativo (ao iniciar as persistence units), e {@code ShutdownEvent} e'
 * disparado antes das tarefas registradas — na pratica o observador roda com o pool vivo.
 *
 * <p>Nunca falha nem trava o shutdown: excecoes e timeout sao logados e absorvidos (no timeout a
 * subscription em voo e' cancelada). O restante pendente e' republicado no proximo boot (semantica
 * at-least-once da outbox).
 */
@ApplicationScoped
public class OutboxShutdownDrain {

    private static final Logger LOGGER = Logger.getLogger(OutboxShutdownDrain.class);

    private final PublishPendingOutboxEvents publisher;
    private final Duration timeout;
    private final Vertx vertx;

    @Inject
    public OutboxShutdownDrain(
            PublishPendingOutboxEvents publisher,
            @ConfigProperty(name = "acme.billing.outbox.shutdown-timeout", defaultValue = "10s") Duration timeout,
            Vertx vertx) {
        this.publisher = publisher;
        this.timeout = timeout;
        this.vertx = vertx;
    }

    void onShutdown(@Observes ShutdownEvent event) {
        drain();
    }

    void drain() {
        Context context = VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext());
        VertxContextSafetyToggle.setContextSafe(context, true);

        CompletableFuture<Void> drained = new CompletableFuture<>();
        DrainSubscription subscription = new DrainSubscription();

        context.runOnContext(ignored -> {
            try {
                subscription.attach(publisher.handle().subscribe().with(
                        item -> drained.complete(null),
                        failure -> completeWithWarning(drained, failure)));
            } catch (RuntimeException failure) {
                completeWithWarning(drained, failure);
            }
        });

        try {
            drained.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            subscription.cancel();
            LOGGER.warnf("drenagem final da outbox interrompida: %s", e.getMessage());
        } catch (TimeoutException e) {
            subscription.cancel();
            LOGGER.warnf("drenagem final da outbox nao concluiu antes do shutdown: %s", e.getMessage());
        } catch (ExecutionException e) {
            subscription.cancel();
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            LOGGER.warnf("drenagem final da outbox falhou: %s", cause.getMessage());
        }
    }

    private void completeWithWarning(CompletableFuture<Void> drained, Throwable failure) {
        LOGGER.warnf("drenagem final da outbox falhou: %s", failure.getMessage());
        drained.complete(null);
    }

    /**
     * Guarda a subscription em voo e tolera a corrida entre o timeout da thread de shutdown e o
     * agendamento do trabalho no event loop: se o cancelamento chegar antes do {@link #attach},
     * o cancelamento e' aplicado assim que a subscription existir. {@code cancel()} e' idempotente.
     */
    private static final class DrainSubscription {

        private Cancellable cancellable;
        private boolean cancelled;

        synchronized void attach(Cancellable cancellable) {
            this.cancellable = cancellable;
            if (cancelled) {
                cancellable.cancel();
            }
        }

        synchronized void cancel() {
            cancelled = true;
            if (cancellable != null) {
                cancellable.cancel();
            }
        }
    }
}
