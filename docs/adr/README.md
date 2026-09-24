# ADRs — Ordem de estudo e implementação

Este diretório registra decisões arquiteturais do projeto.

As ADRs também são material de estudo: devem explicar **por que** uma decisão foi tomada, quais responsabilidades ficam em cada camada, quais alternativas foram consideradas e qual evidência no código demonstra a decisão.

## Ordem de estudo

| Ordem | ADR | Tema | Situação |
|---|---|---|---|
| 1 | [ADR 001 — Idempotência transversal no pipeline de mensagens](./001-messaging-idempotency-middleware.md) | deduplicação por `eventId` no consumo | Accepted / implementada |
| 2 | [ADR 002 — Política de retry para consumo de mensagens](./002-messaging-retry-policy.md) | novas tentativas após `NACK` | Accepted / implementada |
| 3 | [ADR 003 — Dead Letter Queue (DLQ)](./003-dead-letter-queue.md) | destino após esgotar tentativas | Accepted / implementada |
| 4 | [ADR 004 — Transactional outbox](./004-transactional-outbox.md) | consistência entre persistência de negócio e publicação de eventos | Accepted / a implementar (Cap. 9, billing flow) |
| 5 | [ADR 005 — Inbox durável](./005-durable-inbox.md) | idempotência durável associada ao efeito de negócio | Accepted / a implementar (Cap. 9, billing flow) |

### Fundamentos que antecedem essas decisões

Antes dos mecanismos de resiliência, o projeto já possui decisões e contratos relacionados a eventos de integração, versionamento, ownership dos bounded contexts, publicação de `VehicleRegistered` e consumo por Billing.

Esses fundamentos estão documentados em `docs/contracts.md` e `docs/architecture.md`.

## Relação entre as decisões de mensageria

```text
Evento / contrato
      ↓
Publicação
      ↓
Consumo
      ↓
Idempotência
      ↓
Retry
      ↓
DLQ
      ↓
Outbox / Inbox persistentes
```

- **Idempotência:** identifica uma ocorrência já aceita para processamento.
- **Retry:** decide se uma falha deve gerar nova tentativa.
- **DLQ:** define o destino após o limite de tentativas.
- **Outbox:** protege a consistência entre uma transação de negócio e a publicação de evento.
- **Inbox:** fornece deduplicação persistente quando o processamento possui efeito colateral que exige essa garantia.

## Regra para novas ADRs

Uma ADR deve ser criada quando houver uma decisão arquitetural que altere responsabilidades, tenha consequências relevantes ou seja útil para compreender por que o código foi implementado daquela forma.

> A ordem acima é um roteiro de estudo. Uma ADR futura só deve ser marcada como implementada quando existir evidência executável no código e nos testes.