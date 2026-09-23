package org.acme.billing.adapter.in.messaging;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.smallrye.mutiny.Multi;
import io.smallrye.mutiny.Uni;
import io.smallrye.reactive.messaging.PublisherDecorator;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.port.out.ProcessedEventStore;
import org.eclipse.microprofile.reactive.messaging.Message;

import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletionStage;

@ApplicationScoped
public class IdempotencyMessagingDecorator implements PublisherDecorator {

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
            String channelName,
            boolean isConnector) {

        if (!isConnector) {
            return messages;
        }

        return messages
                .onItem()
                .transformToUniAndMerge(this::guard)
                .select()
                .where(Optional::isPresent)
                .map(Optional::get);
    }

    private Uni<Optional<Message<?>>> guard(Message<?> message) {
        UUID eventId = extractEventId(message);

        return processedEventStore.tryClaim(eventId)
                .flatMap(claimed -> {
                    if (!claimed) {
                        return Uni.createFrom()
                                .completionStage(message.ack())
                                .replaceWith(Optional.empty());
                    }

                    Message<?> guardedMessage = message.withNack(
                            failure -> releaseAndNack(eventId, message, failure));

                    return Uni.createFrom().item(Optional.of(guardedMessage));
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
