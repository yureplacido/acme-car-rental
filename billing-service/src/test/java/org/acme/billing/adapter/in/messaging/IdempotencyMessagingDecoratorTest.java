package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.microprofile.reactive.messaging.Message;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

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

        List<Message<?>> result = decorate(decorator, first, duplicate);

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

        List<Message<?>> retried = decorate(
                decorator,
                message(eventId, acknowledgements));

        assertEquals(1, retried.size());
    }

    private static List<Message<?>> decorate(
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
                .indefinitely();
    }

    private static Message<String> message(
            UUID eventId,
            AtomicInteger acknowledgements) {

        return Message.of(
                "{"eventId":"" + eventId + ""}",
                acknowledgements::incrementAndGet);
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
