# ADR 005 — Inbox durável para consumidores de eventos

- **Status:** Accepted / implementada
- **Data:** 2026-09-24
- **Contexto:** Capítulo 9 — Quarkus messaging; pareado com ADR 004 (outbox)
- **Escopo:** billing-service (consumidor dos eventos de cobrança) e demais consumidores futuros

## Contexto

O billing-service deduplica eventos via `ProcessedEventStore.tryClaim(eventId)`. O inbox é persistente em PostgreSQL e o claim é executado pelo `TransactionalInboxProcessor`, na mesma transação reativa do efeito de negócio.

- com **um** broker em **uma** instância, funciona; mas
- um restart perde o conjunto de eventos processados → reprocessamento de tudo que caiu no
  Kafka durante o restart;
- com múltiplas réplicas do billing, cada uma deduplica contra a própria memória → entregas
  duplicadas.

Com o fluxo de cobrança real (ADR 004), o consumidor precisa de deduplicação **durável** entre
restarts e réplicas, porque o evento carrega efeito de negócio (criação/abertura de invoice).

O billing-service **não possui datasource hoje**. Os poms trazem `quarkus-mongodb-panache` e
`quarkus-messaging-rabbitmq` como vestigiais não usados.

## Decisão

1. **Inbox durável em PostgreSQL no billing-service** (nova instância `billing-postgres`,
   imagem `postgres:14`, mesma família do reservation — menor atrito no stack). O billing passa
   a usar `quarkus-hibernate-reactive-panache` + `quarkus-reactive-pg-client`.
2. **`PostgresProcessedEventStore` implementa a porta `ProcessedEventStore`**, mantendo o contrato focado em `tryClaim(UUID)`.
3. **Claim atômico** via `INSERT ... ON CONFLICT (event_id) DO NOTHING`:
   `rowCount == 1` → `true` (primeira vez); `rowCount == 0` → `false` (duplicado). O store não abre uma transação própria.
4. **A fronteira transacional fica no processamento inbound:** `TransactionalInboxProcessor.process(eventId, businessEffect)` executa claim + efeito de negócio dentro da mesma transação reativa `@WithTransaction`.
5. **Retry/DLQ permanece na infraestrutura de messaging:** a política de retry/DLQ (ADR 002/003)
   continua configurada por canal (`delayed-retry-topic`, DLQ por canal) e permanece válida
   para os novos canais `reservation-confirmed-in` e `rental-completed-in`.

### Garantia transacional

O claim e o efeito de negócio compartilham a mesma transação reativa local do PostgreSQL. Se o efeito falhar ou a transação for cancelada, o claim é revertido e o broker pode redeliver o evento. Se a transação concluir com sucesso, o claim e o efeito são commitados juntos.

## Alternativas consideradas

| Alternativa | Veredito | Motivo |
|---|---|---|
| Manter em memória | Rejeitada | Restart/replica duplica efeitos |
| MongoDB no billing | Rejeitada | Vestigial, sem uso; adiciona outra família de DB ao stack |
| RabbitMQ (vestigial no pom) | Rejeitada | Não há broker RabbitMQ no compose; Kafka é o transporte único do repo |
| Claim dentro da tx de negócio | Adotada | `TransactionalInboxProcessor` estabelece a fronteira transacional no adapter inbound |

## Consequências

### Positivas

- deduplicação sobrevive a restart e a múltiplas réplicas;
- elimina os vestigiais `mongodb-panache`/`rabbitmq` do billing;
- middleware e canais de retry/DLQ intocados (ADR 001/002/003 permanecem).

### Negativas

- mais um banco/instância no compose (custo do Cap. 11);
- janela de perda entre claim e efeito (documentada, priorizar em trabalho futuro);
- billing deixa de ser "puro messaging" — ganha datasource (o que habilita persistir invoice,
  ver companheira desta ADR no desenho do Cap. 9).

## Fora do escopo

- exactly-once global entre PostgreSQL e Kafka;
- journaling/compensação de eventos com efeito de negócio já aceito;
- schema registry;
- requeue manual da DLQ.

## Relação com outras ADRs

- **ADR 001 (idempotência):** registra a decisão histórica do middleware; o ADR 007 define a fronteira transacional.
- **ADR 004 (outbox):** produtor at-least-once + consumidor idempotente = entrega efetiva.
- **ADR 002/003 (retry/DLQ):** canais novos herdam a política por configuração.
- **ADR 006 (observabilidade):** métricas do inbox (`processed_events`) entram no Cap. 10.