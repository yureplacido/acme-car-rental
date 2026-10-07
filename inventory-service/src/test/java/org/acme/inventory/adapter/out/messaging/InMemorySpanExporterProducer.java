package org.acme.inventory.adapter.out.messaging;

import io.opentelemetry.sdk.testing.exporter.InMemorySpanExporter;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import jakarta.inject.Singleton;

/**
 * Test-only OpenTelemetry span exporter (guia Quarkus "Using OpenTelemetry",
 * secao "Using CDI to produce a test exporter").
 *
 * <p>Com {@code quarkus.otel.traces.exporter=cdi} (default), o Quarkus usa os
 * exporters gerenciados por CDI. Em testes, este bean substitui o exporter OTLP
 * e permite que os testes de propagacao leiam os spans finalizados em memoria.
 */
@ApplicationScoped
public class InMemorySpanExporterProducer {

    @Produces
    @Singleton
    InMemorySpanExporter inMemorySpanExporter() {
        return InMemorySpanExporter.create();
    }
}