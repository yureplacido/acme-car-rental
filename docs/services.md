# Serviços — ACME Car Rental

> Fonte da verdade: código + docs/domain.md + docs/ddd-tdd-standards.md.

Todos os serviços de negócio seguem o mesmo idioma arquitetural: **Domain → Application → Ports → Adapters**. As diferenças são explicadas pelo bounded context ou pelo papel técnico do módulo.

## inventory-service

**Bounded Context:** Inventory / Fleet.

Responsável pela frota, ciclo de vida e estado operacional dos veículos.

### Aggregate roots

- `Vehicle`
- `MaintenanceOrder`

### Vehicle domain

Value objects:
- `VehicleId`
- `LicensePlate`
- `VehicleSpecifications`
- `VehicleLocation`
- `VehicleDailyRate`
- `OdometerReading`

Concepts:
- `VehicleStatus`: AVAILABLE, IN_MAINTENANCE, DECOMMISSIONED
- `VehicleCondition`: GOOD, NEEDS_INSPECTION, DAMAGED
- `VehicleCategory`
- `Transmission`
- `FuelType`

Behavior:
- registration starts AVAILABLE;
- decommissioning is a state transition/soft delete;
- maintenance state transitions are explicit;
- relocation is explicit;
- odometer cannot decrease;
- condition is explicit;
- base daily rate is immutable.

### Maintenance domain

`MaintenanceOrder` models a maintenance work lifecycle:

`OPEN → IN_PROGRESS → COMPLETED`

with cancellation rules and `MaintenanceOrderId`, `MaintenanceType`, `MaintenanceStatus`.

Future behavior can add scheduled maintenance, inspections, damage assessment and odometer-based maintenance policies.

### Application use cases

- `RegisterVehicle`
- `SearchVehicles`
- `FindVehicleByPlate`
- `ListVehicles`
- `DecommissionVehicle`

### Adapters

Inbound:
- GraphQL
- gRPC

Outbound:
- JPA/Panache + MySQL
- Kafka out (`EventPublisher`) → tópico `vehicle-registered` (cap.9)
- Observability: `/q/metrics` exposto via `quarkus-micrometer-registry-prometheus`; métrica de
  negócio atrás de porta (`InventoryMetrics` → `MicrometerInventoryMetrics`, cap. 10 item 1);
  evidência: `HealthEndpointTest.shouldExposeHttpAndJvmMetrics`
- Tracing: `quarkus-opentelemetry` (cap. 10 item 7) — o record `vehicle-registered` produzido
  pela mutation GraphQL carrega o header `traceparent` do trace da requisição (propagação
  automática, guia Messaging); evidência: `VehicleRegisteredTracePropagationIntegrationTest`

GraphQL exposes a transport DTO named `Car` for compatibility with the existing laboratory contract. It is not a domain object.

The GraphQL adapter maps filtering/sorting/pagination to application query objects; the application use case owns that selection logic.

## reservation-service

**Bounded Context:** Reservation.

Owns the booking commitment between customer and vehicle for a rental period.

Aggregate:
- `Reservation`

Value objects:
- `ReservationId`
- `CustomerId`
- `VehicleId`
- `RentalPeriod`

States:
- PENDING
- CONFIRMED
- CANCELLED
- REJECTED
- COMPLETED

Use cases:
- `CreateReservation`
- `FindAvailableVehicles`
- `ListReservations`

Ports:
- `ReservationRepository`
- `InventoryGateway`
- `RentalGateway`

Adapters:
- REST in
- OIDC/security in
- GraphQL out to Inventory
- REST out to Rental
- Hibernate Reactive/Panache out to PostgreSQL

Availability is derived in the Reservation context by combining Inventory data and reservation conflicts. Inventory does not own period availability.

## rental-service

**Bounded Context:** Rental.

Owns the physical rental lifecycle.

Aggregate:
- `Rental`

Value objects:
- `RentalId`
- `ReservationId`
- `CustomerId`

States:
- PENDING
- ACTIVE
- COMPLETED
- CANCELLED

Use cases:
- `StartRental`
- `EndRental`
- `ListRentals`

Adapter in:
- REST

Adapter out:
- MongoDB/Panache

The domain uses `RentalStatus`; the persistence representation may keep compatibility fields as required by the current schema.

## billing-service

**Bounded Context:** Billing / Payment.

Aggregate:
- `Invoice`

Domain:
- `Invoice`
- `InvoiceLine`
- `InvoiceId`
- `Money`
- `InvoiceStatus`
- `PaymentMethod`
- `PaymentStatus`

The current implementation is a domain/application foundation. Inbound adapters (cap.9): Kafka consumers
`reservation-confirmed-in`/`rental-completed-in`/`vehicle-registered-in` (group `billing-service`)
reagem a eventos de integração de Reservation/Rental/Inventory. O fluxo de cobrança executa
DRAFT→OPEN: `ReservationConfirmed` cria a fatura DRAFT; `RentalCompleted` recompõe a linha com as datas
efetivas (price lock da tarifa diária) e abre a fatura (`CreateInvoice`/`OpenInvoiceForRental`),
persistida em PostgreSQL (Hibernate Reactive Panache).

Idempotência é aplicada pela fronteira transacional `TransactionalInboxProcessor` (ADR 007). O consumer entrega o `eventId` e o efeito de negócio ao processor; o `ProcessedEventStore` permanece atrás de uma porta.

Retry de processamento é tratado na infraestrutura de messaging (estratégia `delayed-retry-topic` do conector
Kafka, `max-retries=3`; ver ADR 002) e a DLQ após o esgotamento (ADR 003). O inbox durável usa
`PostgresProcessedEventStore` com claim atômico (`INSERT ... ON CONFLICT DO NOTHING`; ver ADR 005).

**Outbox transacional (implementada).** A abertura da fatura grava o efeito de negócio **e** o
evento a publicar na mesma transação; a publicação em si é assíncrona:

| Peça | Caminho |
|---|---|
| Caso de uso | `application/usecase/PublishPendingOutboxEvents.java` |
| Porta de saída | `application/port/out/OutboxEventStore.java`, `application/port/out/EventPublisher.java` |
| Modelo de aplicação | `application/model/OutboxEvent.java` |
| Persistência | `adapter/out/persistence/PanacheOutboxEventStore.java`, `adapter/out/persistence/OutboxEventEntity.java` |
| Relay | `adapter/out/messaging/OutboxRelay.java` (`@Scheduled(every="5s")`) |
| Publicador | `adapter/out/messaging/InvoiceOpenedKafkaPublisher.java` (canal `invoice-opened-out`, tópico `invoice-opened`) |

Evidência: `PublishPendingOutboxEventsTest` (aplicação, JUnit puro),
`BillingOutboxIntegrationTest.shouldCommitInvoiceAndOutboxTogetherWhenInvoiceIsOpened`
(os dois commits na mesma transação) e `OutboxRelayKafkaIntegrationTest` (relay → Kafka).
Contrato do evento em [contracts.md](./contracts.md). Decisões: ADR 004 e ADR 008.

**Observability (implementada, cap. 10 item 6).** Métricas do relay e do pipeline Kafka em
`/q/metrics` (formato Prometheus):

| Peça | Caminho |
|---|---|
| Porta de intenção | `application/port/out/OutboxMetrics.java` (`eventRelayed()`/`relayFailed()`) |
| Adapter | `adapter/out/observability/MicrometerOutboxMetrics.java` |
| Métricas | counter `billing.outbox.published`, `billing.outbox.failures`, `billing.outbox.backlog.refresh.errors`; gauge `billing.outbox.pending` |
| Pipeline Kafka | client metrics (lag) via `quarkus.micrometer.binder.kafka.enabled` + channel metrics `quarkus.messaging.message.*` via `smallrye.messaging.observation.enabled=true` |
| Gatilho do gauge | `@Scheduled(every="5s", concurrentExecution = SKIP)` retornando `Uni<Void>` sobre `OutboxEventStore.countPending()` |

Evidência: `MicrometerOutboxMetricsTest` (JUnit puro com `SimpleMeterRegistry`) e
`OutboxMetricsIntegrationTest` (scrape real de `/q/metrics` + corrência com Kafka). Detalhe de
projeto no padrão DDD em [ddd-tdd-standards.md](./ddd-tdd-standards.md) §5‑Observability e
estudo do capítulo em [14-cloud-native-patterns.md](./knowledge/14-cloud-native-patterns.md).

**Tracing (implementada, cap. 10 item 7).** `quarkus-opentelemetry` no pom; propagação de
contexto **automática** no Kafka (guia Messaging, seção OpenTelemetry Tracing): o consumidor de
`vehicle-registered` herda o span do record como pai e processa o evento sob o trace propagado
no header `traceparent` — o contexto viaja no header, nunca no payload. Em dev, o Dev Service
LGTM (Grafana+Tempo) sobe sozinho; em teste, exporter CDI em memória (`InMemorySpanExporter`).
**Escopo atual:** o salto Kafka inventory→billing; os hops REST/GraphQL entre os outros
serviços não são tracejados (não têm o extension). Evidência: `BillingTracePropagationIntegrationTest`.
Decisão: 16 do [architecture.md](./architecture.md).

## users-service

**Role:** Web BFF.

It owns browser-facing orchestration and presentation models, not the Reservation/Inventory domain.

Application:
- `ReservationFacade`

Port:
- `ReservationsGateway`

Adapters:
- Qute/web
- OIDC/security
- REST to reservation

Transport models from reservation remain inside the outbound adapter.

## inventory-cli

**Role:** administrative client.

Application:
- `InventoryAdminGateway`

Adapters:
- CLI in
- gRPC out

Not a bounded context.

## inventory-proto

Contract-only module.

Contains the protobuf schema and build configuration used to generate consumer/server stubs. No domain logic.

## Dependency rule

~~~text
adapter in
   ↓
application
   ↓
domain
   ↑
application port out
   ↑
adapter out
~~~

Models do not cross bounded-context boundaries merely to avoid mapping.

Messaging cross-cutting concerns such as idempotency belong to the messaging infrastructure, not to individual business consumers.

## Migration status

| Module | Domain boundary | Application/use cases | TDD foundation | Special profile |
|---|---|---|---|---|
| inventory-service | ✅ | ✅ | ✅ | business service |
| reservation-service | ✅ | ✅ | ✅ | business service |
| rental-service | ✅ | ✅ | ✅ | business service |
| billing-service | ✅ | ✅ | ✅ | business service foundation |
| users-service | ✅ | ✅ | ✅ | BFF |
| inventory-cli | ✅ | ✅ | — | client |
| inventory-proto | ✅ | — | — | contract |

---
_Last updated: 2026-09-28 (billing/inventory: seção de observability do cap. 10 item 6 —
métricas do relay da outbox e pipeline Kafka em `/q/metrics` — e tracing ponta a ponta do
item 7 — propagação automática de contexto no Kafka com `quarkus-opentelemetry`)._
