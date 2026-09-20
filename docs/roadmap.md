# Roadmap — Quarkus + DDD + TDD

> Cada item é uma evidência executável no repositório, não apenas um tópico lido.

## Foundation
- [x] Padronizar módulos independentes sem reactor
- [x] Definir Bounded Context Map
- [x] Definir padrão Domain/Application/Ports/Adapters
- [x] Definir padrão TDD
- [x] Criar AGENTS.md como regra de engenharia
- [x] Criar agentes OpenCode de DDD, TDD, Quarkus e arquitetura
- [x] Criar agente de implementação orientado pelos padrões
- [x] Criar modelo de domínio enriquecido
- [x] Migrar todos os serviços para o baseline DDD

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

## Cap. 8 — Reactive programming — próximo foco
- [ ] Tornar o fluxo de Inventory explicitamente reativo quando a natureza do caso justificar
- [x] Reservation usa Hibernate Reactive + PostgreSQL reativo
- [x] GraphQL client tipado reativo
- [ ] Demonstrar Uni versus Multi em casos de negócio reais
- [ ] Demonstrar event loop versus worker pool com teste/observabilidade
- [ ] Demonstrar concorrência controlada
- [ ] Demonstrar backpressure em um fluxo de ingestão
- [ ] Definir timeouts/cancellation/retry nos adapters externos

## Cap. 9 — Messaging
- [ ] Billing recebe Reservation/Rental events
- [ ] Definir contratos de eventos
- [ ] Idempotência de consumidores
- [ ] Retry e dead-letter strategy
- [ ] Atualizar contracts.md

## Part 3 — Cloud and beyond
- [ ] Native build e testes integration
- [ ] Kubernetes/OpenShift
- [ ] Observabilidade
- [ ] Resiliência distribuída

## Regra de evolução

Cada feature deve seguir:

~~~text
Behavior
  ↓
RED
  ↓
GREEN
  ↓
REFACTOR
  ↓
Adapter / Integration evidence
~~~

Use os comandos do .opencode antes de aceitar um novo padrão arquitetural.