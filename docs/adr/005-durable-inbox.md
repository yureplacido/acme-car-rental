# ADR 005 — Inbox durável para consumidores de eventos

- **Status:** Accepted (implementação prevista no Capítulo 9)
- **Data:** 2026-09-24
- **Contexto:** Capítulo 9 — Quarkus messaging; pareado com ADR 004 (outbox)
- **Escopo:** billing-service (consumidor dos eventos de cobrança) e demais consumidores futuros

## Contexto

O billing-service deduplica eventos via `ProcessedEventStore.tryClaim(eventId)` aplicado pelo
middleware transversal `IdempotencyMessagingDecorator` (ADR 001). A implementação atual é **em
memória** (`InMemoryProcessedEventStore`, um `ConcurrentHashMap`):

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
2. **`PostgresProcessedEventStore` substitui `InMemoryProcessedEventStore`**, mantendo o
   contrato da porta `ProcessedEventStore` **inalterado** — e o `IdempotencyMessagingDecorator`
   (ADR 001) também não muda: ele trata "já processado" com `tryClaim == false`.
3. **Claim atômico** via `INSERT ... ON CONFLICT (event_id) DO NOTHING`:
   `rowCount == 1` → `true` (primeira vez); `rowCount == 0` → `false` (duplicado). `release`
   faz `DELETE` da linha (para re-tentativas após nack).
4. **Dedicação da infra de consumo é do middleware:** a política de retry/DLQ (ADR 002/003)
   continua configurada por canal (`delayed-retry-topic`, DLQ por canal) e permanece válida
   para os novos canais `reservation-confirmed-in` e `rental-completed-in`.

### Limitação documentada (aceita)

O claim é gravado em **transação própria** antes do efeito de negócio (inserção da invoice).
Se o processo cair entre o claim e o insert da invoice, o evento já estará marcado e, no
redelivery, será deduplicado → invoice perdida.

- O correto é o claim **dentro da mesma transação** do efeito de negócio (inbox e invoice no
  mesmo `@WithTransaction`), o que exige repensar o middleware (hoje transversal, sem acesso
  ao repositório de efeito).
- Esta implementação **documenta o risco no código e na doc** e o aborda como trabalho futuro.
  Para o laboratório, o fluxo nominal é coberto por teste de integração e a janela é aceita.

## Alternativas consideradas

| Alternativa | Veredito | Motivo |
|---|---|---|
| Manter em memória | Rejeitada | Restart/replica duplica efeitos |
| MongoDB no billing | Rejeitada | Vestigial, sem uso; adiciona outra família de DB ao stack |
| RabbitMQ (vestigial no pom) | Rejeitada | Não há broker RabbitMQ no compose; Kafka é o transporte único do repo |
| Claim dentro da tx de negócio | Adiada | Exige refundir o middleware com o repositório de efeito; documentado |

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

- claim na mesma tx do efeito (trabalho futuro documentado);
- journaling/compensação de eventos com efeito de negócio já aceito;
- schema registry;
- requeue manual da DLQ.

## Relação com outras ADRs

- **ADR 001 (idempotência de consumidor):** o middleware permanece; apenas o store vira durável.
- **ADR 004 (outbox):** produtor at-least-once + consumidor idempotente = entrega efetiva.
- **ADR 002/003 (retry/DLQ):** canais novos herdam a política por configuração.
- **ADR 006 (observabilidade):** métricas do inbox (`processed_events`) entram no Cap. 10.