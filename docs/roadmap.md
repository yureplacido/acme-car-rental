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

> Status: pipeline Kafka Inventory → Billing (`vehicle-registered`) executável em código e testes;
> o fluxo de cobrança real (Reservation/Rental → Invoice) ainda não existe.

- [ ] Billing recebe eventos de Reservation/Rental
- [x] Definir contratos de eventos e versionamento
- [x] Idempotência de consumidores
- [ ] Retry / dead-letter strategy
- [ ] Outbox/inbox quando o domínio exigir consistência entre DB e eventos

Notas de escopo:

- Billing hoje consome `vehicle-registered` do Inventory (scaffold de aprendizagem; o handler só loga).
  O fluxo de cobrança (Reservation/Rental → Invoice) ainda não existe.
- Idempotência é uma preocupação transversal do pipeline de messaging e agora é aplicada pelo
  `IdempotencyMessagingDecorator`, antes do consumer de negócio. O consumer não depende diretamente
  de `ProcessedEventStore`.
- `ProcessedEventStore.tryClaim(UUID)` representa o claim atômico. A implementação atual é em memória;
  a Inbox persistente/durável continua pendente até existir efeito colateral de negócio real.
- A decisão arquitetural está registrada em `docs/adr/001-messaging-idempotency-middleware.md`.
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
