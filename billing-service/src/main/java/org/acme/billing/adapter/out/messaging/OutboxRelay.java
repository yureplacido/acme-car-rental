package org.acme.billing.adapter.out.messaging;

import io.quarkus.scheduler.Scheduled;
import io.smallrye.mutiny.Uni;
import jakarta.enterprise.context.ApplicationScoped;
import org.acme.billing.application.usecase.PublishPendingOutboxEvents;

@ApplicationScoped
public class OutboxRelay {

    private final PublishPendingOutboxEvents publisher;

    public OutboxRelay(PublishPendingOutboxEvents publisher) {
        this.publisher = publisher;
    }

    @Scheduled(
            every = "5s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> relay() {
        return publisher.handle();
    }
}
