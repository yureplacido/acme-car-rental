# ADR 007 — Inbox transacional com o efeito de negócio

- **Status:** Accepted / implementada
- **Data:** 2026-09-24
- **Contexto:** Capítulo 9 — Quarkus Messaging
- **Escopo:** billing-service e futuros consumidores com efeito persistente local

## Contexto

O inbox durável resolveu a deduplicação entre restarts e réplicas, mas a implementação anterior fazia o claim em uma transação própria. Isso criava uma janela:

```text
BEGIN claim
  ↓
COMMIT
  ↓
efeito de negócio
  ↓
crash
```

Se o processo caísse depois do commit do claim e antes do efeito, o redelivery encontraria o evento como processado e o efeito poderia ser perdido.

A transação precisa proteger o **claim e o efeito de negócio juntos**.

## Decisão

O `billing-service` usa `TransactionalInboxProcessor` como fronteira de processamento inbound.

```text
Kafka
  ↓
Reactive Messaging
  ↓
Consumer / Adapter
  ↓
TransactionalInboxProcessor @WithTransaction
  ├── ProcessedEventStore.tryClaim(eventId)
  │      ├── false → duplicata → sucesso sem efeito
  │      └── true
  │           ↓
  │       business effect
  │           ↓
  └──────── COMMIT
```

Regras:

1. `ProcessedEventStore.tryClaim` **não cria uma transação própria**.
2. O `INSERT ... ON CONFLICT DO NOTHING` participa da transação aberta pelo processor.
3. O efeito de negócio também retorna `Uni` e permanece dentro da mesma pipeline reativa.
4. Falha ou cancelamento do `Uni` provoca rollback do claim e do efeito.
5. Sucesso confirma os dois juntos.
6. O broker continua responsável por ACK/NACK e retry/DLQ; isso não vira exactly-once global.

A fronteira é implementada com `@WithTransaction`, compatível com Hibernate Reactive/Panache para métodos CDI que retornam `Uni`.

## Por que não manter o PublisherDecorator?

O `PublisherDecorator` é útil para preocupações que não precisam compartilhar uma transação com o efeito de negócio. Para o inbox, porém, o claim precisa conhecer a fronteira de processamento.

Manter o claim no decorator faria o claim acontecer antes do consumer e reintroduziria a janela de perda. Por isso o decorator de idempotência foi removido e o processor passou a ser chamado explicitamente pelos adapters inbound.

Isso não espalha a regra de persistência: os consumers conhecem apenas a abstração de processamento transacional; o store continua atrás da porta `ProcessedEventStore`.

## Consequências

### Positivas

- elimina a janela claim-commit → efeito;
- rollback libera automaticamente o evento para retry;
- a política de retry/DLQ continua separada do mecanismo de consistência;
- o domínio continua sem dependência de Quarkus;
- a fronteira transacional fica explícita e testável.

### Limitações

- a garantia é local ao PostgreSQL do Billing;
- Kafka e PostgreSQL continuam sistemas distintos;
- não existe exactly-once global;
- efeitos externos fora da mesma transação local exigem outbox/idempotência própria.

## Evidência

- `TransactionalInboxProcessor`
- `PostgresProcessedEventStore`
- `TransactionalInboxProcessorIntegrationTest`
- `BillingFlowKafkaIntegrationTest`

O teste de integração do processor demonstra que uma falha do efeito faz o claim voltar a ficar disponível.
