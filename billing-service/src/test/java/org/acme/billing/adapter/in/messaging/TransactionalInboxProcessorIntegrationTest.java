package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TransactionalInboxProcessorIntegrationTest {

    @Inject
    TransactionalInboxProcessor processor;

    @Inject
    PgPool pgPool;

    @Test
    @RunOnVertxContext
    void shouldMakeEventAvailableAgainWhenBusinessEffectFails(UniAsserter asserter) {
        UUID eventId = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();

        asserter.execute(() -> processor.process(
                eventId,
                () -> Uni.createFrom().failure(
                        new IllegalStateException("simulated business failure")))
                .onFailure().recoverWithNull());

        asserter.execute(() -> processor.process(
                eventId,
                () -> Uni.createFrom().voidItem().invoke(effects::incrementAndGet))
                .onFailure().recoverWithNull());

        asserter.assertThat(
                () -> Uni.createFrom().item(effects::get),
                count -> assertEquals(1, count));
    }

    @Test
    @RunOnVertxContext
    void shouldCommitInboxClaimWithBusinessEffect(UniAsserter asserter) {
        UUID eventId = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();

        asserter.execute(() -> processor.process(
                eventId,
                () -> Uni.createFrom().voidItem().invoke(effects::incrementAndGet)));
        asserter.execute(() -> processor.process(
                eventId,
                () -> Uni.createFrom().voidItem().invoke(effects::incrementAndGet)));
        asserter.assertThat(
                () -> Uni.createFrom().item(effects::get),
                count -> assertEquals(1, count));
    }

    @Test
    @RunOnVertxContext
    void shouldAllowOnlyOneConcurrentInboxClaim(UniAsserter asserter) {
        UUID eventId = UUID.randomUUID();
        int attempts = 20;

        List<Uni<Boolean>> claims = java.util.stream.IntStream.range(0, attempts)
                .mapToObj(ignored -> claimDirectlyAgainstPostgres(eventId))
                .toList();

        asserter.assertThat(
                () -> Multi.createFrom().iterable(claims)
                        .onItem().transformToUniAndMerge(Function.identity())
                        .collect().asList(),
                results -> {
                    assertEquals(attempts, results.size());
                    assertEquals(1, results.stream().filter(Boolean.TRUE::equals).count());
                    assertEquals(attempts - 1, results.stream().filter(Boolean.FALSE::equals).count());
                });
    }

    private Uni<Boolean> claimDirectlyAgainstPostgres(UUID eventId) {
        return pgPool.withTransaction(connection ->
                connection.preparedQuery("""
                                INSERT INTO processed_event (event_id)
                                VALUES ($1)
                                ON CONFLICT (event_id) DO NOTHING
                                """)
                        .execute(io.vertx.mutiny.sqlclient.Tuple.of(eventId.toString()))
                        .map(result -> result.rowCount() == 1));
    }
}
