package org.acme.billing.application.port.out;

/**
 * Métricas de negócio do relay da outbox, isoladas atrás de uma porta da
 * aplicação. A implementação concreta (Micrometer) vive no adapter.
 */
public interface OutboxMetrics {

    void eventRelayed();

    void relayFailed();
}