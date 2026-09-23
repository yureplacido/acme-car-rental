package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.SubscriberDecorator;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.eclipse.microprofile.config.Config;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class IdempotencyMessagingDecorator implements SubscriberDecorator {

    private final ObjectMapper objectMapper;
    private final ProcessedEventStore processedEventStore;

    public IdempotencyMessagingDecorator(
            ObjectMapper objectMapper,
            ProcessedEventStore processedEventStore) {
        this.objectMapper = objectMapper;
        this.processedEventStore = processedEventStore;
    }

    @Override
    public Multi<? extends Message<?>> decorate(
            Multi<? extends Message<?>> messages,
            List<String> channelNames,
            Config channelConfig) {

        if (channelConfig == null) {
            return messages;
        }

        return messages
                .onItem()
                .transformToUniAndMerge(this::guard);
    }

    private Uni<Message<?>> guard(Message<?> message) {
        UUID eventId = extractEventId(message);

        return processedEventStore.tryClaim(eventId)
                .flatMap(claimed -> {
                    if (!claimed) {
                        return Uni.createFrom()
                                .completionStage(message.ack())
                                .replaceWithNull();
                    }

                    return Uni.createFrom().item(
                            message.withNack(failure ->
                                    releaseAndNack(eventId, message, failure)));
                });
    }

    private CompletionStage<Void> releaseAndNack(
            UUID eventId,
            Message<?> message,
            Throwable failure) {

        return processedEventStore.release(eventId)
                .chain(() -> Uni.createFrom().completionStage(message.nack(failure)))
                .subscribeAsCompletionStage();
    }

    private UUID extractEventId(Message<?> message) {
        try {
            JsonNode root = objectMapper.readTree(
                    String.valueOf(message.getPayload()));

            String eventId = root.path("eventId").asText(null);

            if (eventId == null || eventId.isBlank()) {
                throw new IllegalArgumentException(
                        "Message does not contain a valid eventId");
            }

            return UUID.fromString(eventId);
        } catch (Exception e) {
            if (e instanceof IllegalArgumentException
                    && "Message does not contain a valid eventId".equals(e.getMessage())) {
                throw (IllegalArgumentException) e;
            }

            throw new IllegalArgumentException(
                    "Could not extract eventId from message payload", e);
        }
    }

    @Override
    public int getPriority() {
        return 100;
    }
}
