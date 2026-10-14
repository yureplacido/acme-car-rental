package org.acme.billing.adapter.out.messaging;

import io.quarkus.hibernate.reactive.panache.Panache;
import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Context;
import io.vertx.core.Vertx;
import io.vertx.mutiny.pgclient.PgPool;
import io.vertx.mutiny.sqlclient.Tuple;
import jakarta.inject.Inject;
import org.acme.billing.adapter.in.messaging.BillingKafkaCompanionResource;
import org.acme.billing.application.event.InvoiceOpened;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Drena a outbox a partir de uma thread SEM contexto Vert.x, que e' exatamente o ambiente da
 * thread de shutdown (o Quarkus dispara o ShutdownEvent de forma sincrona em
 * {@code beanManager().getEvent().fire(...)}). Sem contexto, o Hibernate Reactive Panache
 * ({@code @WithSession}) lanca {@code IllegalStateException("No current Vertx context found")},
 * que a drenagem precisa absorver — mas absorver nao pode virar "nao drenar nada".
 *
 * <p>O evento e' consultado por id (nao por um {@code findPending(100)} limitado), entao o teste
 * prova presenca antes e publicacao depois: nao passa vacuamente por o evento nao ter sido
 * persistido nem por ter escapado de um lote.
 */
@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class OutboxShutdownDrainIntegrationTest {

    @Inject
    OutboxEventStore outboxEventStore;

    @Inject
    OutboxShutdownDrain drain;

    @Inject
    PgPool pgPool;

    @Test
    @RunOnVertxContext
    void shouldDrainPendingOutboxWhenTriggeredFromAThreadWithoutVertxContext(UniAsserter asserter) {
        Context eventLoop = Vertx.currentContext();
        assertNotNull(eventLoop, "o teste deve rodar sobre um contexto Vert.x (@RunOnVertxContext)");

        InvoiceOpened event = new InvoiceOpened(
                UUID.randomUUID(),
                1,
                Instant.now(),
                "invoice-" + UUID.randomUUID(),
                "customer-1",
                "reservation-1",
                new BigDecimal("120.00"),
                "BRL");

        asserter.execute(() -> Panache.withTransaction(
                () -> outboxEventStore.appendInvoiceOpened(event)));

        asserter.assertThat(() -> count(event.eventId(), false),
                pending -> assertEquals(1L, pending, "o evento deve estar pendente antes da drenagem"));

        asserter.execute(() -> onWorkerThread(() -> {
            runDrainOnShutdownThread();
            return null;
        }).emitOn(command -> eventLoop.runOnContext(ignored -> command.run())).replaceWithVoid());

        asserter.assertThat(() -> count(event.eventId(), false),
                pending -> assertEquals(0L, pending, "o evento nao pode continuar pendente apos o shutdown"));
        asserter.assertThat(() -> count(event.eventId(), true),
                published -> assertEquals(1L, published, "o evento deve ter sido marcado como publicado no shutdown"));
    }

    private Uni<Long> count(UUID eventId, boolean published) {
        String sql = "SELECT count(*) AS total FROM outbox_event"
                + " WHERE event_id = $1 AND published_at IS " + (published ? "NOT NULL" : "NULL");

        return pgPool.withConnection(connection ->
                connection.preparedQuery(sql)
                        .execute(Tuple.of(eventId.toString()))
                        .map(rows -> rows.iterator().next().getLong("total")));
    }

    private void runDrainOnShutdownThread() {
        Thread shutdownThread = new Thread(drain::drain, "simulated-shutdown-thread");
        shutdownThread.start();
        try {
            shutdownThread.join(Duration.ofSeconds(15).toMillis());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("interrompido esperando a thread de shutdown", e);
        }

        assertFalse(shutdownThread.isAlive(), "a drenagem final nao pode travar a thread de shutdown");
    }

    private <T> Uni<T> onWorkerThread(Supplier<T> blockingCall) {
        return Uni.createFrom().item(blockingCall)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }
}
