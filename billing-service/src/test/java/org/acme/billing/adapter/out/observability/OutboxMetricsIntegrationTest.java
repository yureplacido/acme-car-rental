package org.acme.billing.adapter.out.observability;

import io.quarkus.test.common.QuarkusTestResource;
import io.quarkus.test.common.http.TestHTTPResource;
import io.quarkus.test.junit.QuarkusTest;
import io.quarkus.test.vertx.RunOnVertxContext;
import io.quarkus.test.vertx.UniAsserter;
import io.smallrye.mutiny.Uni;
import io.smallrye.mutiny.infrastructure.Infrastructure;
import io.vertx.core.Vertx;
import jakarta.inject.Inject;
import org.acme.billing.adapter.in.messaging.BillingKafkaCompanionResource;
import org.acme.billing.application.port.out.OutboxEventStore;
import org.acme.billing.application.usecase.CreateInvoice;
import org.acme.billing.application.usecase.OpenInvoiceForRental;
import org.acme.billing.application.usecase.PublishPendingOutboxEvents;
import org.acme.billing.domain.model.InvoiceLine;
import org.acme.billing.domain.model.Money;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.Executor;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class OutboxMetricsIntegrationTest {

    private static final Duration SCRAPE_POLL = Duration.ofSeconds(20);
    private static final Pattern PUBLISHED_COUNTER =
            Pattern.compile("billing_outbox_published_total\\s+([0-9.]+)");
    private static final Pattern PENDING_GAUGE =
            Pattern.compile("billing_outbox_pending\\s+([0-9.]+)");

    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5))
            .build();

    @TestHTTPResource("/q/metrics")
    URI metricsUrl;

    @Inject
    CreateInvoice createInvoice;

    @Inject
    OpenInvoiceForRental openInvoice;

    @Inject
    PublishPendingOutboxEvents publishPending;

    @Inject
    OutboxEventStore outboxEventStore;

    @Inject
    MicrometerOutboxMetrics outboxMetrics;

    @Test
    @RunOnVertxContext
    void shouldExposeOutboxRelayAndKafkaPipelineMetrics(UniAsserter asserter) {
        Executor eventLoop = command ->
                Vertx.currentContext().runOnContext(ignored -> command.run());
        String reservationId = "metrics-" + UUID.randomUUID();
        AtomicReference<String> baseline = new AtomicReference<>();
        AtomicLong expectedBacklog = new AtomicLong();

        CreateInvoice.Command create = new CreateInvoice.Command(
                "customer-1",
                reservationId,
                List.of(InvoiceLine.rentalDays(
                        "Aluguel de veículo ABC-1234",
                        LocalDate.of(2026, 9, 25),
                        LocalDate.of(2026, 9, 27),
                        new Money(new BigDecimal("100.00"), "BRL"))));

        asserter.assertThat(
                () -> onWorkerThread(this::scrapeMetrics).emitOn(eventLoop),
                baseline::set);

        asserter.assertThat(
                () -> createInvoice.handle(create)
                        .flatMap(invoice -> openInvoice.handle(
                                new OpenInvoiceForRental.Command(
                                        new OpenInvoiceForRental.RentalDetails(
                                                reservationId,
                                                LocalDate.of(2026, 9, 26),
                                                LocalDate.of(2026, 9, 28),
                                                new Money(new BigDecimal("120.00"), "BRL"),
                                                "ABC-1234")))),
                opened -> assertNotNull(opened.id().value()));

        asserter.assertThat(
                () -> publishPending.handle(),
                ignored -> { });

        asserter.assertThat(
                () -> outboxEventStore.countPending()
                        .flatMap(count -> {
                            expectedBacklog.set(count);
                            return outboxMetrics.refreshBacklog();
                        }),
                ignored -> { });

        asserter.assertThat(
                () -> onWorkerThread(this::scrapeMetrics).emitOn(eventLoop),
                body -> {
                    double before =
                            metricValue(baseline.get(), PUBLISHED_COUNTER);
                    double after =
                            metricValue(body, PUBLISHED_COUNTER);
                    assertTrue(
                            after >= before + 1.0,
                            "relaying the outbox event must bump the published counter:\n" + body);
                    assertEquals(
                            expectedBacklog.get(),
                            metricValue(body, PENDING_GAUGE),
                            0.0,
                            "the backlog gauge must reflect the real outbox backlog:\n" + body);
                });

        asserter.assertThat(
                () -> onWorkerThread(() -> awaitMetricLine(
                        "kafka_consumer_fetch_manager_records_lag_max")),
                line -> assertTrue(line.isPresent(),
                        "Kafka client metrics (consumer lag) must be exported"));

        asserter.assertThat(
                () -> onWorkerThread(() -> awaitMetricLine(
                        "quarkus_messaging_message_count_total{channel=\"invoice-opened-out\"")),
                line -> assertTrue(line.isPresent(),
                        "per-channel message metrics must be exported (smallrye.messaging.observation.enabled=true)"));
    }

    private <T> Uni<T> onWorkerThread(Supplier<T> blockingCall) {
        return Uni.createFrom().item(blockingCall)
                .runSubscriptionOn(Infrastructure.getDefaultExecutor());
    }

    private String scrapeMetrics() {
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(metricsUrl)
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());
            assertEquals(200, response.statusCode(), "metrics endpoint must be reachable");
            return response.body();
        } catch (Exception e) {
            throw new IllegalStateException("Could not scrape " + metricsUrl, e);
        }
    }

    private Optional<String> awaitMetricLine(String metricSubstring) {
        long deadline = System.nanoTime() + SCRAPE_POLL.toNanos();
        String lastBody = "";
        while (System.nanoTime() < deadline) {
            lastBody = scrapeMetrics();
            if (lastBody.contains(metricSubstring)) {
                return lastBody.lines()
                        .filter(line -> line.contains(metricSubstring))
                        .findFirst();
            }
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        throw new AssertionError(
                "metric '" + metricSubstring + "' not found within "
                        + SCRAPE_POLL.toSeconds() + "s, last scrape:\n" + lastBody);
    }

    private static double metricValue(String body, Pattern sample) {
        Matcher matcher = sample.matcher(body);
        if (!matcher.find()) {
            return -1.0;
        }
        return Double.parseDouble(matcher.group(1));
    }
}