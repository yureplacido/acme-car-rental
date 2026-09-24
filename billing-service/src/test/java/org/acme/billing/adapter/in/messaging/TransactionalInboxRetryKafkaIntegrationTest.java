package org.acme.billing.adapter.in.messaging;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.kafka.InjectKafkaCompanion;
import io.smallrye.reactive.messaging.kafka.companion.KafkaCompanion;
import io.vertx.mutiny.pgclient.PgPool;
import jakarta.inject.Inject;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

@QuarkusTest
@QuarkusTestResource(BillingFlowKafkaCompanionResource.class)
class TransactionalInboxRetryKafkaIntegrationTest {

    private static final String TOPIC = "transactional-inbox-retry-test";

    @InjectKafkaCompanion
    KafkaCompanion companion;

    @Inject
    TransactionalInboxRetryTestConsumer consumer;

    @Inject
    PgPool pgPool;

    @BeforeEach
    void reset() {
        consumer.reset();
        companion.topics().createAndWait(TOPIC, 1);
    }

    @Test
    void shouldRollbackInboxClaimAndProcessSuccessfullyOnDelayedRetry()
            throws Exception {
        UUID eventId = UUID.randomUUID();

        companion.produceStrings()
                .fromRecords(new ProducerRecord<>(TOPIC, eventId.toString(), eventId.toString()))
                .awaitCompletion();

        consumer.success().get(30, TimeUnit.SECONDS);

        assertEquals(2, consumer.attempts());
        assertEquals(1, consumer.successfulEffects());

        long processedEvents = pgPool.withConnection(connection ->
                connection.query("""
                                SELECT COUNT(*)
                                FROM processed_event
                                WHERE event_id = $1
                                """)
                        .execute(io.vertx.mutiny.sqlclient.Tuple.of(eventId.toString()))
                        .map(rows -> rows.iterator().next().getLong(0)))
                .await().indefinitely();

        assertEquals(1L, processedEvents);
    }
}
