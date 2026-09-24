# ADR 004 — Transactional outbox nos produtores de eventos de negócio

- **Status:** Accepted (implementação prevista no Capítulo 9)
- **Data:** 2026-09-24
- **Contexto:** Capítulo 9 — Quarkus messaging; pareado com ADR 005 (inbox durável)
- **Escopo:** produtores de eventos de integração cuja publicação precisa ser consistente com a escrita de negócio

## Contexto

O fluxo de cobrança real (roadmap Cap. 9) exige que eventos de negócio cheguem ao Billing
de forma confiável. O padrão ingênuo — gravar o agregado e, em seguida, enviar para o Kafka —
tem uma janela de inconsistência:

- se a mensagem for publicada antes do commit da transação, um consumidor pode processar um
  evento de um agregado que ainda não existe (ou será revertido);
- se o commit acontecer e a publicação falhar, o evento é perdido (invoice nunca criada).

Emissão direta com `Emitter`/`@Outgoing` (estilo do livro §9.4/§9.6) é aceitável quando o
consumidor tolera a perda ou o produtor pode reenviar manualmente. No caso da cobrança isso
não vale: um `ReservationConfirmed` perdido significa uma fatura que nunca nasce.

O repositório tem dois produtores candidatos:

| Serviço | Evento | Banco | Outbox na mesma tx? |
|---|---|---|---|
| reservation-service | `ReservationConfirmed` (cobrança do compromisso) | PostgreSQL + Hibernate Reactive | ✅ viável |
| rental-service | `RentalCompleted` (fechamento de locação) | MongoDB | ❌ não atômico com a escrita de negócio |

## Decisão

1. **Transactional outbox** no **reservation-service**: a linha do outbox é gravada na **mesma
   transação reativa** (PostgreSQL) que persiste o agregado `Reservation`. A publicação para o
   Kafka é feita por um **relay** que lê linhas não publicadas e as marca como publicadas.
2. **Sem outbox no rental-service**: o evento `RentalCompleted` é publicado com um **emitter
   Kafka direto** após a escrita no Mongo. Justificativa: um evento perdido deixa a Invoice
   **aberta** (DRAFT), observável e recuperável — o efeito de negócio principal (a própria
   locação) não depende do evento; o requisito de consistência estrita não se aplica aqui.
3. **Mecanismo:** a fronteira transacional fica no adapter de persistência (padrão atual —
   `PanacheReservationRepository.save` já é `@WithTransaction`): novo método
   `saveWithOutbox(Reservation, ReservationConfirmed)` grava agregado + linha de outbox no
   mesmo `@WithTransaction`. O evento é serializado para JSON no adapter.
4. **Relay:** `@Scheduled` (padrão `concurrentExecution=SKIP`) busca linhas não publicadas em
   lote, publica via emitter Kafka típado e marca `published_at`. Se a publicação falha, a
   linha permanece não publicada e a próxima execução tenta de novo.
5. **Garantia:** *at least once*. Uma mensagem pode ser publicada duas vezes se o processo
   falhar entre o publish e o `markPublished`; a **deduplicação é responsabilidade do
   consumidor** via inbox (ADR 005).

### Posições e entidades (reservation-service)

```
application/port/out/ReservationRepository   + Uni<Reservation> saveWithOutbox(Reservation, ReservationConfirmed)
application/port/out/OutboxEventStore        findUnpublished(limit) / markPublished(eventId)
application/port/out/EventPublisher          publish(key, payload)
adapter/out/persistence/OutboxEventEntity    id, eventId(unique), eventType, aggregateId, payload, occurredAt, publishedAt(nullable)
adapter/out/persistence/PanacheReservationRepository  saveWithOutbox (@WithTransaction)
adapter/out/persistence/PanacheOutboxEventStore       findUnpublished/markPublished
adapter/out/messaging/KafkaEventPublisher     @Channel("reservation-confirmed-out") MutinyEmitter
adapter/out/messaging/OutboxRelay             @Scheduled
```

### Enriquecimento do evento (taxa no booking)

`ReservationConfirmed` carrega um **snapshot da taxa diária** capturada no momento da reserva
(price lock, regra de negócio: a fatura reflete a taxa acordada no booking). Isso torna o evento
autocontido para o Billing (sem chamada síncrona no caminho de consumo). O reservation já
consulta o inventory por GraphQL (`InventoryGateway`); o gateway ganha `findVehicle(vehicleId)`
e o modelo `AvailableVehicle` ganha `dailyRateAmount`/`dailyRateCurrency` (campo aditivo —
`daily_rate` já é exposto pelo inventory).

## Alternativas consideradas

| Alternativa | Veredito | Motivo |
|---|---|---|
| Emitter direto no reservation (como o livro) | Rejeitada | `ReservationConfirmed` é portador da cobrança; perda implicaria invoice inexistente |
| Outbox no Mongo do rental | Rejeitada | MongoDB não dá atomicidade entre a escrita de negócio e a linha do outbox |
| Debezium/CDC da tabela outbox | Adiada | Overhead para o laboratório; relay `@Scheduled` atende ao volume |
| Publicação direto no use case | Rejeitada | Política de transporte não pertence à aplicação (AGENTS: adapters) |

## Consequências

### Positivas

- `ReservationConfirmed` nasce com consistência com a persistência da reserva;
- fluxo de erro simples: relay idempotente + dedupe no consumidor (ADR 005);
- camada de aplicação não conhece Kafka (porta `EventPublisher`).

### Negativas

- mais uma tabela no PostgreSQL do reservation;
- latência de publicação igual ao intervalo de polling do relay (até ~5s);
- at-least-once força o consumidor a ser idempotente (ADR 005 é pré-requisito).

## Fora do escopo

- CDC (Debezium), retry macro do relay, particionamento por aggregate,
  retrofit do `vehicle-registered` (inventory) para outbox — adiados/documentados;
- idempotência do consumidor → ADR 005.

## Relação com outras ADRs

- **ADR 005 (inbox durável):** consumidor deduplica por `eventId` — pré-requisito do at-least-once.
- **ADR 002/003 (retry/DLQ):** o relay publica uma única vez; se o Billing falhar, retry/DLQ
  atuam no consumo, não na publicação.
- **ADR 006 (observabilidade):** métricas do outbox (relay) e contadores de tentativas ficam
  disponíveis via Micrometer no Cap. 10.