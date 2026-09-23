package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class IdempotencyMessagingDecoratorTest {

    @Test
    void shouldDropDuplicateMessagesBeforeTheyReachTheConsumer() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        IdempotencyMessagingDecorator decorator =
                new IdempotencyMessagingDecorator(new ObjectMapper(), store);

        UUID eventId = UUID.randomUUID();
        AtomicInteger acknowledgements = new AtomicInteger();

        Message<String> first = message(eventId, acknowledgements);
        Message<String> duplicate = message(eventId, acknowledgements);

        List<? extends Message<?>> result = decorate(decorator, first, duplicate);

        assertEquals(1, result.size());
        assertEquals(1, store.claims());
        assertEquals(1, acknowledgements.get());
    }

    @Test
    void shouldReleaseClaimWhenConsumerNacksTheMessage() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        IdempotencyMessagingDecorator decorator =
                new IdempotencyMessagingDecorator(new ObjectMapper(), store);

        UUID eventId = UUID.randomUUID();
        AtomicInteger acknowledgements = new AtomicInteger();

        Message<?> message = decorate(
                decorator,
                message(eventId, acknowledgements))
                .getFirst();

        message.nack(new IllegalStateException("processing failed"))
                .toCompletableFuture()
                .join();

        assertEquals(0, store.claims());

        List<? extends Message<?>> retried = decorate(
                decorator,
                message(eventId, acknowledgements));

        assertEquals(1, retried.size());
    }

    @Test
    void shouldNotDecorateChannelsWithoutConnectorConfig() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        IdempotencyMessagingDecorator decorator =
                new IdempotencyMessagingDecorator(new ObjectMapper(), store);

        Message<String> first = message(UUID.randomUUID(), new AtomicInteger());
        Message<String> second = message(UUID.randomUUID(), new AtomicInteger());

        List<? extends Message<?>> result = decorator
                .decorate(
                        Multi.createFrom().items(first, second),
                        List.of("some-emitter"),
                        null)
                .collect()
                .asList()
                .await()
                .atMost(Duration.ofSeconds(5));

        assertEquals(2, result.size());
        assertEquals(0, store.claims());
    }

    @Test
    void shouldPassThroughMessagesWhoseEventIdCannotBeExtracted() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        IdempotencyMessagingDecorator decorator =
                new IdempotencyMessagingDecorator(new ObjectMapper(), store);

        AtomicInteger acknowledgements = new AtomicInteger();
        Message<String> corrupt = Message.of(
                "{\"noEventId\":true}",
                () -> {
                    acknowledgements.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                });

        List<? extends Message<?>> result = decorate(decorator, corrupt);

        assertEquals(1, result.size());
        assertSame(corrupt, result.getFirst());
        assertEquals(0, store.claims());
        assertEquals(0, acknowledgements.get());
    }

    @Test
    void shouldPassThroughMessagesThatAreNotJson() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        IdempotencyMessagingDecorator decorator =
                new IdempotencyMessagingDecorator(new ObjectMapper(), store);

        List<? extends Message<?>> result = decorate(
                decorator,
                Message.of("not-json"));

        assertEquals(1, result.size());
        assertEquals(0, store.claims());
    }

    @Test
    void shouldPassThroughMessagesWhoseEventIdIsNotAUuid() {
        InMemoryProcessedEventStore store = new InMemoryProcessedEventStore();
        IdempotencyMessagingDecorator decorator =
                new IdempotencyMessagingDecorator(new ObjectMapper(), store);

        List<? extends Message<?>> result = decorate(
                decorator,
                Message.of("{\"eventId\":\"not-a-uuid\"}"));

        assertEquals(1, result.size());
        assertEquals(0, store.claims());
    }

    private static List<? extends Message<?>> decorate(
            IdempotencyMessagingDecorator decorator,
            Message<?>... messages) {

        return decorator
                .decorate(
                        Multi.createFrom().items(messages),
                        List.of("vehicle-registered-in"),
                        ConfigProvider.getConfig())
                .collect()
                .asList()
                .await()
                .atMost(Duration.ofSeconds(5));
    }

    private static Message<String> message(
            UUID eventId,
            AtomicInteger acknowledgements) {

        return Message.of(
                "{\"eventId\":\"" + eventId + "\"}",
                () -> {
                    acknowledgements.incrementAndGet();
                    return java.util.concurrent.CompletableFuture.completedFuture(null);
                });
    }

    static class InMemoryProcessedEventStore implements ProcessedEventStore {

        private final ConcurrentHashMap.KeySetView<UUID, Boolean> processed =
                ConcurrentHashMap.newKeySet();

        @Override
        public Uni<Boolean> tryClaim(UUID eventId) {
            return Uni.createFrom().item(() -> processed.add(eventId));
        }

        @Override
        public Uni<Void> release(UUID eventId) {
            processed.remove(eventId);
            return Uni.createFrom().voidItem();
        }

        int claims() {
            return processed.size();
        }
    }
}
