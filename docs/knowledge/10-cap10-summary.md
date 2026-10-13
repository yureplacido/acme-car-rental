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

## 10.5 Service Discovery (Consul + Stork)

- Stork com discovery via Consul em `%dev/%docker/%test` (com auto-registro controlado)
- Discovery via Kubernetes em `%kubernetes` (Stork K8s) — item 11.1
- Seleção de testes por backend: profile `kubernetes` exclui `@Tag("consul")`, baseline exclui `@Tag("kubernetes")`

Evidências: `ReservationsServiceDiscoveryTest` (Consul) e `ReservationsServiceDiscoveryKubernetesTest` (K8s)

## 10.6 Reactive Messaging (Kafka)

- SmallRye Reactive Messaging + Kafka
- Outbox transacional (cap.9) integrado ao pipeline
- Kafka companions com rede determinística (cap.11, correção de Pool overlaps)

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
