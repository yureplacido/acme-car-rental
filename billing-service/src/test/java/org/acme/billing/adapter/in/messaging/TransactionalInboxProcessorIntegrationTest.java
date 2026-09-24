package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
class TransactionalInboxProcessorIntegrationTest {

    @Inject
    TransactionalInboxProcessor processor;

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
}
