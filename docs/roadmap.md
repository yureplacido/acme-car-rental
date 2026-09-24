# Roadmap — Quarkus + DDD + TDD

> Cada item é uma evidência executável no repositório, não apenas um tópico lido.

## Foundation

- [x] Padronizar módulos independentes sem reactor
- [x] Agregador de testes na raiz (conveniência; sem parent/acoplamento) — `./mvnw test`
- [x] Definir Bounded Context Map
- [x] Definir padrão Domain/Application/Ports/Adapters
- [x] Definir padrão TDD
- [x] Criar AGENTS.md como regra de engenharia
- [x] Criar agentes OpenCode de arquitetura, DDD, TDD e Quarkus
- [x] Criar agente de implementação orientado pelos padrões
- [x] Criar **Domain Designer** para definir o modelo antes da implementação
- [x] Definir Inventory como Fleet/Vehicle bounded context
- [x] Enriquecer Inventory com Vehicle telemetry/condition
- [x] Criar MaintenanceOrder como segundo aggregate do Inventory
- [x] Migrar todos os business services para o DDD baseline
- [x] Estruturar Users como BFF e CLI/Proto como módulos especiais

## Part 1 — Getting started

- [x] Cap. 1-2 — Quarkus e primeira aplicação
- [x] Cap. 3 — Dev mode e continuous testing

## Part 2 — Developing applications

- [x] Cap. 4 — REST, GraphQL e gRPC
- [x] Cap. 5 — Testing
- [x] Cap. 6 — Web, OIDC e segurança
- [x] Cap. 7 — Database access
- [ ] Cap. 8 — Reactive programming
- [ ] Cap. 9 — Quarkus Messaging

## Cap. 8 — Reactive programming

- [x] Reservation usa Hibernate Reactive + PostgreSQL reativo
- [x] Reservation usa GraphQL client tipado reativo
- [ ] Tornar o fluxo de Inventory explicitamente reativo quando a natureza do caso justificar
- [ ] Demonstrar Uni versus Multi em casos de negócio reais
- [ ] Demonstrar event loop versus worker pool com teste/observabilidade
- [ ] Demonstrar concorrência controlada
- [ ] Demonstrar backpressure em um fluxo de ingestão
- [ ] Definir timeout/cancellation/retry nos adapters externos

## Cap. 9 — Messaging

> Status: pipeline Kafka Inventory → Billing (`vehicle-registered`) executável em código, testes e
> stack docker-compose (Kafka provisionado via `kafka-init`), com retry (ADR 002) e DLQ (ADR 003);
> o fluxo de cobrança (Reservation/Rental → Invoice DRAFT→OPEN) é executável em código e testes
> (Kafka → Postgres via Dev Services), com inbox durável (ADR 005).

- [x] Billing recebe eventos de Reservation/Rental
- [x] Definir contratos de eventos e versionamento
- [x] Idempotência de consumidores
- [x] Retry (delayed-retry-topic, ADR 002)
- [x] Kafka provisionado no stack docker (broker KRaft + tópicos via `kafka-init`)
- [x] Dead-letter strategy (ADR 003)
- [x] Outbox/inbox quando o domínio exigir consistência entre DB e eventos

Notas de escopo:

- Billing hoje consome além de `vehicle-registered` (scaffold de aprendizagem; o handler só loga):
  os eventos `ReservationConfirmed`/`RentalCompleted` do fluxo de cobrança — DRAFT→OPEN — em
  `KafkaReservationConfirmedConsumer`/`KafkaRentalCompletedConsumer`, validado por
  `BillingFlowKafkaIntegrationTest` (Kafka real + Postgres). O teste usa `UniAsserter` (sem transação
  envolvente) para que cada leitura veja o efeito commitado pelo consumer; com
  `TransactionalUniAsserter` o cache de primeira camada enxergava sempre o DRAFT e mascarava o UPDATE.
- Idempotência é aplicada no processamento inbound pelo `TransactionalInboxProcessor`: claim e efeito de negócio
  compartilham a mesma transação reativa. O `ProcessedEventStore` permanece atrás de uma porta.
- `ProcessedEventStore.tryClaim(UUID)` representa o claim atômico. A implementação persistente em
  Postgres (`INSERT ... ON CONFLICT DO NOTHING`) cobre o inbox durável (ADR 005); em memória fica
  apenas para os testes de unidade/application.
- Retry é configurado na infraestrutura Kafka (`delayed-retry-topic`, `max-retries=3`, atrasos 1s/5s/15s);
  decisão em `docs/adr/002-messaging-retry-policy.md`. Após o esgotamento, o record vai para a DLQ
  `vehicle-registered-dlq` (decisão em `docs/adr/003-dead-letter-queue.md`). Eventos corruptos também
  percorrem a política: o decorator de idempotência repassa ao consumer (sem claim) o que não consegue
  extrair `eventId`; a falha ocorre no consumer e segue retry até a DLQ; validado por teste de integração
  (`DlqKafkaIntegrationTest`, canal de teste `dlq-test-in`) e E2E no stack (`SRMSG18278` encadeado até
  `vehicle-registered-dlq`).
- O stack docker provisiona Kafka via `others/docker-compose.yml` (serviços `kafka` e `kafka-init`,
  broker `apache/kafka:3.9.1`). Ferramentas de operação: `docker exec acme-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server kafka:9092 ...`.
  A imagem é **`3.9.1` e não `3.9.0`** por causa do bug **KAFKA-18281**: com KRaft 3.9.0 o broker
  validava listeners não-advertised (ex.: `CONTROLLER`) contra `advertised.listeners` e o `0.0.0.0`
  causava falha de inicialização/healthcheck com a nossa configuração — corrigido em 3.9.1.
- A decisão arquitetural da fronteira transacional está registrada em `docs/adr/007-transactional-inbox.md`.
- Contrato documentado em `docs/contracts.md` (seção `VehicleRegistered (Kafka)`).

## Part 3 — Cloud and beyond

- [ ] Native build e testes integration
- [ ] Kubernetes/OpenShift
- [ ] Observabilidade
- [ ] Resiliência distribuída

## Regra de evolução

```
Domain Design
      ↓
RED
      ↓
GREEN
      ↓
REFACTOR
      ↓
Adapter / Integration evidence
      ↓
Architecture + DDD + TDD + Quarkus guardians
```

Use `/domain-design` antes de implementar uma feature e `/preflight` para o fluxo completo.
