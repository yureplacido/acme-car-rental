package org.acme.billing.application;

import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import jakarta.inject.Inject;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.acme.billing.application.usecase.TransactionalInboxProcessor;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
class TransactionalInboxProcessorIntegrationTest {

    @Inject
    TransactionalInboxProcessor processor;

    @Inject
    ProcessedEventStore processedEventStore;

    @Test
    @RunOnVertxContext
    void shouldRollbackInboxClaimWhenBusinessEffectFails(UniAsserter asserter) {
        UUID eventId = UUID.randomUUID();

        asserter.assertThat(
                () -> processor.process(
                                eventId,
                                () -> Uni.createFrom().failure(
                                        new IllegalStateException("simulated business failure")))
                        .onFailure().recoverWithNull()
                        .chain(() -> processedEventStore.tryClaim(eventId)),
                claimed -> assertTrue(claimed));
    }

    @Test
    @RunOnVertxContext
    void shouldCommitInboxClaimWithBusinessEffect() {
        UUID eventId = UUID.randomUUID();
        AtomicInteger effects = new AtomicInteger();

        processor.process(eventId, () -> Uni.createFrom().voidItem()
                        .invoke(effects::incrementAndGet))
                .await().indefinitely();

        processor.process(eventId, () -> Uni.createFrom().voidItem()
                        .invoke(effects::incrementAndGet))
                .await().indefinitely();

        assertEquals(1, effects.get());
    }
}
