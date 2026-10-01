# Testes — ACME Car Rental

> Estratégia única para todo o repositório. Cada serviço aplica as camadas de teste de acordo com seu papel.
> Para o **porquê** de cada regra (e as lições das armadilhas reais), ver a base de estudo:
> [knowledge/04-estrategia-de-testes-do-projeto.md](./knowledge/04-estrategia-de-testes-do-projeto.md)
> e [knowledge/11-armadilhas-e-licoes.md](./knowledge/11-armadilhas-e-licoes.md).

## Pirâmide de testes

~~~text
              Integration / Native
                     ▲
             Adapter / Contract
                     ▲
            Application Use Cases
                     ▲
               Domain tests
~~~

### Domain
JUnit puro.

Sem Quarkus, CDI, banco, HTTP, GraphQL, gRPC, Panache ou Mutiny.

Objetivo: provar invariantes e comportamentos do domínio.

Exemplos atuais:
- inventory-service/src/test/java/org/acme/inventory/domain/VehicleTest.java
- reservation-service/src/test/java/org/acme/reservation/domain/ReservationTest.java
- rental-service/src/test/java/org/acme/rental/domain/RentalTest.java
- billing-service/src/test/java/org/acme/billing/domain/InvoiceTest.java

### Application
Testes JVM dos casos de uso usando ports/fakes/mocks.

Exemplos atuais: RegisterVehicle, CreateReservation, StartRental, CreateInvoice, ReservationFacade.

O teste deve poder rodar sem subir a aplicação Quarkus.

### Adapter
Usar @QuarkusTest quando a fronteira/framework é parte do comportamento.

Exemplos: Reservation REST, Reservation persistence reativa, GraphQL/gRPC contracts e OIDC/security.

### Observability
O teste de um adapter de métrica segue **duas camadas**, nunca uma:

1. **Lógica do adapter** (contadores, gauge, regra no-caminho-de-falha) = **JUnit puro** com um
   `SimpleMeterRegistry` real, sem Quarkus. Exemplos: `MicrometerOutboxMetricsTest`,
   `MicrometerInventoryMetricsTest` (`knowledge/04` §9, "Adapter unit").
2. **Evidência de ponta a ponta** (o registro é exposto) = `@QuarkusTest` que scaneia o
   `/q/metrics` real, exigindo `quarkus-micrometer-registry-prometheus` no pom do serviço.
   Série é **nome + tag** (não `startsWith` do nome). Exemplos: `OutboxMetricsIntegrationTest`,
   `HealthEndpointTest` (inventory).

Regras: nunca `await().indefinitely()` sob `@RunOnVertxContext` (usar `UniAsserter`); scrape
HTTP de um endpoint em worker thread exige `.emitOn(eventLoop)` para o próximo passo reativo
voltar à event loop; métrica de negócio é sempre declarada como **porta** no serviço — o teste
de application usa o fake da porta.

### Tracing
O teste de propagação de contexto usa o padrão oficial "Using CDI to produce a test exporter":
`io.opentelemetry:opentelemetry-sdk-testing` (scope `test`) + um bean `@Produces @Singleton`
de `InMemorySpanExporter` em `src/test` (o Quarkus usa exporters CDI quando
`quarkus.otel.traces.exporter=cdi`, o default). Em `%test` desligar o exporter OTLP
(`%test.quarkus.otel.exporter.otlp.enabled=false`) e usar `%test.quarkus.otel.simple=true`
(exporta na hora, sem esperar o batch de 5s).

- **Saída (inventory):** `@QuarkusTest` executa a mutation GraphQL, consome o record do broker
  com `KafkaCompanion` e verifica o header `traceparent` bem formado + traceId de um span do
  próprio serviço. Exemplo: `VehicleRegisteredTracePropagationIntegrationTest`.
- **Entrada (billing):** `@QuarkusTest` publica um record com `traceparent` conhecido
  (upstream simulado) e verifica no `InMemorySpanExporter` que o processamento gerou um span
  com o traceId do header. Exemplo: `BillingTracePropagationIntegrationTest`.

Atenção: com `quarkus-opentelemetry` presente, o Micrometer anexa **exemplars OTel** às linhas
de contador na saída **OpenMetrics 1.0** (o que o `/q/metrics` devolve sem `Accept: text/plain`;
o formato Prometheus 0.0.4, pedido com `Accept: text/plain`, não tem exemplars):
`name 1.0 # {span_id=...,trace_id=...} 1.0 <ts>`. Matchers de métrica devem usar `Pattern` +
`find()` (primeiro número após o nome), nunca `matches()` de linha inteira.

### Integration / native
@QuarkusIntegrationTest é reservado para validar o artefato empacotado e o runtime.

## TDD

Cada mudança de comportamento deve seguir:

~~~text
Specification
     ↓
RED
     ↓
GREEN
     ↓
REFACTOR
~~~

Um teste criado depois da implementação não é evidência suficiente de TDD.

Os commits devem preferencialmente manter essa história visível: test(...), feat(...), refactor(...).

## Nomenclatura
Prefira nomes como shouldRejectReservationWhenVehicleIsAlreadyReserved e evite nomes que descrevem implementação.

## Reactive testing
Testes reativos devem provar comportamento da pipeline. Não use await().indefinitely() para esconder um contrato assíncrono em código que deveria permanecer não bloqueante.

Em teste anotado com @RunOnVertxContext (que roda na event loop do Vert.x) é proibido await().indefinitely(): o await bloqueia a thread que precisa entregar o item, gerando deadlock. Nesses casos use UniAsserter/AssertSubscriber, ou isole a operação bloqueante com runSubscriptionOn(...).runSubscriptionOnIn(workerThread). Ver docs/knowledge/04-estrategia-de-testes-do-projeto.md §5.

Para Hibernate Reactive, o Quarkus fornece suporte específico de teste e exige contexto/sessão reativa apropriados. Ver <https://quarkus.io/guides/hibernate-reactive-panache>.
---
_Last updated: 2026-09-28 (subseções "Observability" e "Tracing": teste de adapter de métrica em
duas camadas; régua da série por nome+tag; propagação de contexto via `InMemorySpanExporter` e
armadilha dos exemplars OTel)._
