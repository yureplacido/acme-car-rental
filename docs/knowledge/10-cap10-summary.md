# Cap. 10 — Observabilidade, Resiliência e Service Discovery (consolidação)

Capítulo 10 implementa seis pilares: **SmallRye Health**, **Micrometer/Prometheus**, **OpenTelemetry/Tracing**, **SmallRye Fault Tolerance**, **Service Discovery (Consul + Stork)** e **Reactive Messaging (Kafka)**. Esta nota consolida decisões, evidências e links.

## 10.1 Health (SmallRye Health)

- Endpoints: `/q/health/live`, `/q/health/ready`, `/q/health/started`
- `HealthEndpointTest`: valida readiness, liveness e startup (caminhos públicos em `%test`)
- Readiness considera dependências (DB/Kafka/OIDC) conforme Quarkus/SR Health
- Evidências em código/tests e manifests K8s com probes reaproveitando endpoints (cap.11)

Links: [health](../testing.md#health), [architecture.md](../architecture.md), testes em `*HealthEndpointTest.java`

## 10.2 Metrics (Micrometer + Prometheus)

- Micrometer expõe `/q/metrics` (Prometheus format)
- Métricas de aplicação e de infraestrutura conforme configuração por serviço
- Annotations Prometheus nos manifests K8s

## 10.3 Tracing (OpenTelemetry)

- `quarkus-opentelemetry` habilitado; propagação de contexto (W3C TraceContext) entre serviços
- Kafka: propagação automática via OTLP/SMR Reactive Messaging (contexto via headers)
- Não exporta OTLP em `%prod/%docker` por padrão (evita logs de falha); configurável com collector

Evidência: tracing ponta-a-ponta validado (cap.10 itens 7).

## 10.4 Fault Tolerance (SmallRye FT)

- Retries, timeouts, circuit breakers, fallback conforme uso nos adapters outbound (chamadas a serviços externos: reservation→inventory/billing)
- Aplicado em paths reativos, respeitando `Uni`/não-blocking (regra 14 AGENTS.md)
- **Lição do capítulo**: a política vive no **adapter de saída**, e o lado que **consome** um contrato de falha não precisa repetir a política de quem o produz. O `users-service` (BFF) consome o `GET /reservations/availability` e limita o **transporte** (`connect-timeout=500`, `read-timeout=3000`, numa configKey própria —
`quarkus.rest-client.<configKey>.*` é **por cliente**, e juntar leitura e escrita não idempotente
numa só teria aplicado o prazo curto ao `POST /reservations`) em vez de instalar `quarkus-smallrye-fault-tolerance`: o reservation já tem timeout/retry/fallback e já devolve 503, então repetir aqui só multiplicaria a carga sobre um serviço em falha. A taxonomia da falha é **medida** contra o cliente real dos dois lados (`GraphQLInventoryClientFailureTest` no reservation, `ReservationsClientFailureTest` no BFF) — tipos deduzidos da documentação viram `@Retry`/`@Fallback` letra morta com teste verde. Decisão em [ADR 009 §6](../adr/009-fault-tolerance-chamadas-externas.md).
- **Armadilha correlata**: lista vazia nunca é degradação. `InventoryUnavailable` (reservation) e `AvailabilityUnavailable` (BFF) existem porque `200 []` significa "não há veículo" — devolvê-lo quando a consulta falhou é resposta de negócio falsa.

## 10.5 Service Discovery (Consul + Stork)

- Stork com discovery via Consul em `%dev/%docker/%test` (com auto-registro controlado)
- Discovery via Kubernetes em `%kubernetes` (Stork K8s) — item 11.1
- Seleção de testes por backend: profile `kubernetes` exclui `@Tag("consul")`, baseline exclui `@Tag("kubernetes")`

Evidências: `ReservationsServiceDiscoveryTest` (Consul) e `ReservationsServiceDiscoveryKubernetesTest` (K8s)

## 10.6 Reactive Messaging (Kafka)

- SmallRye Reactive Messaging + Kafka
- Outbox transacional (cap.9) integrado ao pipeline
- Kafka companions com rede determinística (cap.11, correção de Pool overlaps)

## 10.7 Graceful shutdown

- `quarkus.shutdown.timeout` (guia oficial Lifecycle, 3.39) nos 5 serviços: o Quarkus aguarda requisições HTTP em voo terminarem antes de encerrar.
- **Limite importante**: apenas HTTP é drenado automaticamente. Trabalho agendado (o relay da outbox) **não** é aguardado — por isso o billing tem `OutboxShutdownDrain` (`@Observes ShutdownEvent`) que faz uma drenagem final bounded (`acme.billing.outbox.shutdown-timeout`, default 10s).
- **Armadilha (Hibernate Reactive em lifecycle observer)**: o `ShutdownEvent` é disparado de forma síncrona em `beanManager().getEvent().fire(...)`, ou seja, numa thread comum **sem contexto Vert.x**. Como o caso de uso usa Panache (`@WithSession`), isso lançava `IllegalStateException("No current Vertx context found")` — a drenagem virava um no-op silencioso. O drain agora cria um contexto Vert.x duplicado e marcado como seguro (`VertxContext.createNewDuplicatedContext(vertx.getOrCreateContext())` + `VertxContextSafetyToggle.setContextSafe(context, true)`), despacha o `handle()` via `context.runOnContext(...)` e a thread de shutdown aguarda num `CompletableFuture` bounded pelo timeout.
- O drain nunca falha nem trava o shutdown: exceções e timeout são logados e absorvidos; o restante pendente é republicado no próximo boot (at-least-once).
- Evidência: `OutboxShutdownDrainTest` (publica pendentes; no-op sem pendentes; não falha em erro de publicação; não trava no timeout; cancela a subscription em voo no timeout) + `OutboxShutdownDrainIntegrationTest` (evento real semeado, drenado a partir de `new Thread("simulated-shutdown-thread")` sem contexto Vert.x, com Kafka companion) — billing 70/70 verde.

## Decisões & ADRs

- ADR 011: imagens e perfis cloud-native (Consul vs K8s)
- ADRs relacionados a outbox/transações/messaging (004/008/009) referenciados

## Links

- [testing.md](../testing.md)
- [architecture.md](../architecture.md)
- [services.md](../services.md)
- [knowledge/08-messaging-reativo.md](./08-messaging-reativo.md)
- [knowledge/13-transactional-outbox.md](./13-transactional-outbox.md)
- Manifests: [others/k8s/](../../others/k8s/)
