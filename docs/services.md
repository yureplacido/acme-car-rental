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
- `BulkRegisterVehicles` — import massivo reativo (`Multi<Command>` → `Multi<Vehicle>`), com
  concorrência limitada por `inventory.bulk.max-concurrency` (default 4, fora do domínio),
  política fail-fast e cancelamento das irmãs em voo (ADR 006)
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
- REST out to Rental (via Stork: `stork://rentals`, catálogo Consul)
- Hibernate Reactive/Panache out to PostgreSQL

Availability is derived in the Reservation context by combining Inventory data and reservation conflicts. Inventory does not own period availability.

Aqui mora a política de fault tolerance das duas chamadas síncronas de saída (decisão em
[adr/009](adr/009-fault-tolerance-chamadas-externas.md)):

- `adapter/out/rental/RentalRestGateway` (escrita): **só timeout**. Sem `@Retry` porque a escrita
  não é idempotente, sem `@Fallback` porque engolir a falha confirmaria reserva sem locação.
- `adapter/out/inventory/GraphQLInventoryGateway` (leitura): timeout, `@Retry` só para falha
  transitória (deadline, resposta HTTP sem envelope GraphQL, conexão recusada/resetada — lista
  medida contra o cliente real em `GraphQLInventoryClientFailureTest`) e fallback que sinaliza
  `InventoryUnavailable` em vez de devolver lista vazia. Erro de GraphQL (200 com `errors`) não
  é repetido nem convertido em indisponibilidade.
- `adapter/in/rest/InventoryUnavailableMapper`: transforma o sinal em 503 com corpo estável
  (`adapter/in/rest/model/InventoryUnavailableResponse`) e `Retry-After`, sem vazar detalhe de
  infraestrutura.
- A política é **das annotations dos adapters**, não das portas nem dos casos de uso;
  os prazos operacionais ficam em `application.properties`
  (`quarkus.fault-tolerance."<classe>/<método>".*`), com `timeout.unit` sempre explícito.

Service discovery (cap. 10 item 9): as duas saídas síncronas **não** compartilham o mesmo
mecanismo. A REST para o rental usa **Stork + Consul** (`stork://rentals`): a localização sai da
configuração e vira resolução de instância no catálogo. A publicação é feita por **adapter
próprio de registro** (`reservation-service` e `rental-service` têm
`adapter/out/registration/ConsulServiceRegistration`): publicam `rentals`/`reservations` no boot
com health check HTTP absoluto e deregistram no shutdown — o auto-registro do Stork tem dois
defectos na 3.39.3 documentados no [ADR 010](./adr/010-service-discovery.md) (health-check-url
relativa → critical desde o boot; dereg depois do CDI fechado). A GraphQL para o inventory
**não** está no Stork — o Stork integra REST Client e gRPC, e o cliente aqui é o SmallRye GraphQL
Client — e segue com URL externalizada (`INVENTORY_SERVICE_URL`): **divergência documentada** do
item 9. Evidências em `%test` (ao vivo, com Consul de testcontainers):
`RentalRestGateway`→`RentalClient` atravessa a fronteira via a instância `rentals` do catálogo
(`RentalServiceDiscoveryTest`), e cada serviço que se publica tem teste de registro que nega os
defectos acima (`ReservationRegistersInConsulTest`, `RentalRegistersInConsulTest`: check
**passing** no boot e saída limpa do catálogo após o deregister).

O `users-service` também tem chamada síncrona de saída (`ReservationsRestGateway` →
`GET /reservations/availability`) e é **consumidor** do contrato de falha acima: não repete nem
re-tenta, traduz. O cliente de leitura é bloqueante e **tem configKey própria**
(`AvailabilityClient`, `quarkus.rest-client.reservations-availability.connect-timeout=500`,
`read-timeout=3000`) — não por capricho: `quarkus.rest-client.<configKey>.*` é configuração por
cliente, e a escrita `POST /reservations` que compartilhava a `ReservationsClient` não é
idempotente, então o prazo curto da leitura não pode alcançá-la. Antes não havia prazo nenhum e o
caminho de falha segurava thread do BFF por ~9 s. A taxonomia de falha foi **medida e fixada**
contra o cliente REST real (`ReservationsClientFailureTest`, com `assertInstanceOf`): resposta
não-2xx chega como `WebApplicationException`, conexão recusada e estouro de prazo como
`ProcessingException`; os dois viram `AvailabilityUnavailable` (`application/exception`), nunca
lista vazia. A causa é logada no `catch` (`LOG.warnf`, mesmo lugar do análogo `GraphQLInventoryGateway`) —
necessária porque o mapper consome a exceção. A fronteira responde 503 com `Retry-After: 30` e o
fragmento explicativo (`AvailabilityUnavailableMapper` + `availabilityunavailable.html`); o
`index.html` registra um `htmx:beforeSwap` escopado ao alvo `#availability` porque o htmx não troca
respostas de erro por padrão. Decisão na [ADR 009 §6](adr/009-fault-tolerance-chamadas-externas.md),
contrato em [contracts.md](contracts.md).

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

**Graceful shutdown (implementada, cap. 10 item 8).** `quarkus.shutdown.timeout=30s` nos 5 serviços
faz o Quarkus aguardar requisições HTTP em voo. Trabalho agendado não é aguardado, então o billing
fecha com `adapter/out/messaging/OutboxShutdownDrain.java` (`@Observes ShutdownEvent`): drenagem final
bounded por `acme.billing.outbox.shutdown-timeout` (default 10s), que nunca falha nem trava o shutdown.
Como o observador roda sem contexto Vert.x e o caso de uso usa Hibernate Reactive/Panache, a drenagem
cria um contexto duplicado e marcado como seguro. A janela do K8s (`terminationGracePeriodSeconds`) é
fixada em 45s pelo `scripts/generate-manifests.sh` (o quarkus-kubernetes 3.39.3 não expõe config para
isso) para cobrir os 30s de HTTP + 10s da outbox. Evidência: `OutboxShutdownDrainTest` e
`OutboxShutdownDrainIntegrationTest`.


## users-service

**Role:** Web BFF.

It owns browser-facing orchestration and presentation models, not the Reservation/Inventory domain.

Application:
- `ReservationFacade`

Ports:
- `ReservationsGateway`

Application signals:
- `application/exception/AvailabilityUnavailable` — "não deu para saber", distinto de lista vazia

Adapters:
- Qute/web (`ReservationsResource`, templates em `ReservationsTemplates`)
- inbound de falha: `AvailabilityUnavailableMapper` (→ 503 + `Retry-After` + fragmento)
- OIDC/security
- REST to reservation (via Stork: `stork://reservations`, catálogo Consul) — dois clientes:
  `ReservationsClient` (listagem e escrita) e `AvailabilityClient` (leitura, com prazo próprio)

Transport models from reservation remain inside the outbound adapter.

A saída resolve a localização do reservation no Consul (`stork://reservations`), publicado pelo
adapter próprio de registro (`ConsulServiceRegistration`) como `reservations`. Como os dois clientes
carregam `@AccessToken`, o caminho completo só roda dentro de request autenticado (o filtro aborta 401
sem token). Por isso a prova de discovery do BFF (`ReservationsServiceDiscoveryTest`) atua no nível do
**Stork** — resolução da instância no catálogo + requisição direta ao endereço resolvido — em vez de
dentro do cliente OIDC; o caminho completo REST Client→Stork→Consul fica evidenciado no
reservation-service (sem token).

O BFF é o único consumidor do contrato de falha do reservation e trata a indisponibilidade como
**tradução, não como repetição** (decisão na [ADR 009 §6](adr/009-fault-tolerance-chamadas-externas.md)):

- `adapter/out/reservation/ReservationsRestGateway.availability` converte
  `WebApplicationException | ProcessingException` em `AvailabilityUnavailable`. A lista de tipos
  é **medida e fixada** contra o cliente REST real em `ReservationsClientFailureTest`
  (com `assertInstanceOf`, então upgrade que mude a taxonomia fica vermelho), não deduzida —
  mesma lição do lado do reservation (`GraphQLInventoryClientFailureTest`).
- O prazo é **do transporte** (`connect-timeout=500`, `read-timeout=3000`), porque o cliente é
  bloqueante; não há `quarkus-smallrye-fault-tolerance` neste módulo. Um `@Retry` aqui
  multiplicaria o custo de um serviço já em falha, sem acrescentar sinal novo. O prazo mora numa
  configKey própria (`AvailabilityClient` / `reservations-availability`), porque
  `quarkus.rest-client.<configKey>.*` é por cliente e a escrita `POST /reservations` (não
  idempotente) não pode herdar o prazo da leitura — guarda em
  `shouldNotPutTheAvailabilityDeadlineOnTheNonIdempotentWrite`.
- `adapter/in/web/AvailabilityUnavailableMapper` responde **503** com `Retry-After: 30` e o
  fragmento `availabilityunavailable.html`. Nunca 200 com lista vazia — lista vazia significaria
  "nenhum veículo", que é uma mentira de negócio.
- A causa vai para `LOG.warnf` no `catch` do gateway, nunca para o corpo
  (`ReservationsAvailabilityUnavailableTest.shouldNotLeakInfrastructureDetailWhenAvailabilityIsUnavailable`).
  O log é obrigatório: o mapper consome a exceção, então o Quarkus não a loga sozinho — sem ele
  o mascaramento acima ficaria sem diagnóstico.
- O `index.html` registra um `htmx:beforeSwap` **escopado ao alvo `#availability`**, que força a
  troca das respostas 503: sem ele o htmx descarta o corpo de erro e a tabela não muda em
  silêncio. O escopo evita que um 503 de `/get` ou `/reserve` seja trocado em silêncio também.

Custo conhecido: **qualquer** falha upstream vira 503 do BFF, inclusive um 500 determinístico do
reservation. É mascarada deliberada para não vazar infraestrutura ao browser, registrada como
consequência da ADR 009.

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

## Runtime por ambiente e imagem (cap.10 item 10)

Os 5 serviços publicam a **mesma imagem** `acme/<artifactId>:<version>` (Maven profile
`docker`, `quarkus-container-image-docker`); o comportamento do runtime é config no launch via
`QUARKUS_PROFILE` — não rebuild:

- `%docker` (compose): Consul para a descoberta (`consul-host=consul`), registro dos publishers
  pela ADR 010, keycloak/o banco/mensageria por nome de serviço no compose.
- `%kubernetes` (cluster): Stork provider `kubernetes` nos que descobrem (`users`,
  `reservation`) com `k8s-namespace`; `%kubernetes.acme.consul.
  registration.enabled=false` nos publishers (reservation e rental — quem publica em K8s é a
  plataforma). O dev service do kubernetes-client é desligado globalmente (vale p/ dev/test
  também — ver `application.properties` de users/reservation). `use-endpoint-slices` não é
  fixado no runtime: o provider auto-deteta EndpointSlices/Endpoints, e só o mock dos testes
  (`@Tag("kubernetes")`) pinça `false` para o caminho de Endpoints ser determinístico.
- `%prod` (jar no host): localhost para tudo, registra no Consul `localhost:8500` quando ativo.

Os testes de discovery seguem o mesmo eixo: `@Tag("consul")` (default no CI) e
`@Tag("kubernetes")` (mock do API server, com `-P kubernetes` no Maven). Manifests Kubernetes
versionados em `others/k8s/`. Decisão e evidência em
[docs/adr/011-imagens-e-perfis-cloud-native.md](adr/011-imagens-e-perfis-cloud-native.md).

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
_Last updated: 2026-10-07 (item 9 do cap. 10 — service discovery: users e reservation resolvem o
destino via Stork/Consul (`stork://reservations`, `stork://rentals`), rental e reservation se
publicam no catálogo com health check HTTP absoluto e deregistram no shutdown via adapter próprio
(`ConsulServiceRegistration`) — o auto-registro do Stork foi descartado na 3.39.3 (ver ADR 010);
divergência documentada: a saída GraphQL para o inventory fica com URL externalizada)._
