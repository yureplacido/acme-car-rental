# 09 — Padrões de resiliência em messaging

> Capítulo 9 do livro **+ as decisões do projeto** registradas em [adr/](../adr/README.md).
> Aqui não há exemplo "do livro": há **decisão nossa**, com a alternativa descartada e a
> evidência em teste.

## 0. Sumário das decisões

| ADR | Tema | Status |
|---|---|---|
| [001](../adr/001-messaging-idempotency-middleware.md) | idempotência no pipeline | Superseded por 007 |
| [002](../adr/002-messaging-retry-policy.md) | política de retry | Accepted / implementada |
| [003](../adr/003-dead-letter-queue.md) | dead-letter queue | Accepted / implementada |
| [004](../adr/004-transactional-outbox.md) | transactional outbox | Accepted / implementada |
| [005](../adr/005-durable-inbox.md) | inbox durável | Accepted / implementada |
| [007](../adr/007-transactional-inbox.md) | inbox **transacional** | Accepted / implementada |
| [008](../adr/008-transactional-outbox.md) | outbox transacional | Accepted / implementada |
| [001-reactive-inventory-bulk-import](../adr/001-reactive-inventory-bulk-import.md) | import massivo de veículos | Proposed |

> ⚠️ **Colisão de numeração:** existem dois ADR `001-` (`001-messaging-idempotency-middleware`
> e `001-reactive-inventory-bulk-import`). Sempre referencie pelo **nome do arquivo**, não
> pelo número. Os dois estão listados em [adr/README.md](../adr/README.md), que também traz
> o aviso de colisão.

## 1. As três perguntas que separam as responsabilidades

A separação que o projeto fez explicitamente (ADR 002):

| Camada | Pergunta que responde |
|---|---|
| **Idempotência** | "Esta ocorrência de evento já foi aceita para processamento?" |
| **Retry** | "Uma falha de processamento deve resultar em nova tentativa?" |
| **DLQ** | "O que fazer quando as tentativas acabar?" |

Cada uma tem **um** dono. Retry dentro do consumer? Não. Idempotência dentro do caso de uso?
Não. DLQ dentro da aplicação? Não — é infraestrutura de broker.

## 2. Idempotência (ADR 001 → 007)

### 2.1 O problema

Kafka é **at-least-once**. O mesmo evento chega duas vezes se: o consumer processa, faz
`ack`, e o offset não foi confirmado antes de o consumer cair. Ou se a transação de
negócio faz commit e o `ack` não acontece.

Resultado: `Invoice` criado duas vezes para a mesma reservation.

### 2.2 Evolução da decisão

| Versão | O que fazia | Problema |
|---|---|---|
| ADR 001 | middleware de idempotência por `eventId` | cada consumer precisava lembrar de usá-lo |
| ADR 005 | inbox **durável** em Postgres (`INSERT ... ON CONFLICT DO NOTHING`) | claim em transação **própria** |
| ADR 007 | inbox **transacional**: claim + efeito na **mesma** transação | — |

A janela que a ADR 007 fechou:

```text
antes:  claim (commit)  →  efeito de negócio (falha)  →  evento perdido para sempre
depois: claim + efeito (uma transação)  →  falha = rollback dos dois  →  retry pode reclaimar
```

```java
@ApplicationScoped
public class TransactionalInboxProcessor implements InboundEventProcessor {
    @Override
    @WithTransaction
    public Uni<Void> process(UUID eventId, Supplier<Uni<Void>> businessEffect) {
        return processedEventStore.tryClaim(eventId)
                .flatMap(claimed -> claimed ? businessEffect.get() : Uni.createFrom().voidItem());
    }
}
```

Teste que prova as duas metades do contrato
(`TransactionalInboxProcessorIntegrationTest`):

- `shouldMakeEventAvailableAgainWhenBusinessEffectFails` — falhou ⇒ o `eventId` **pode** ser
  reclaimer;
- `shouldCommitInboxClaimWithBusinessEffect` — sucesso ⇒ o segundo `process` do mesmo
  `eventId` é **ignorado**.

## 3. Retry (ADR 002)

### 3.1 Onde fica

Na **infraestrutura** de messaging, via `failure-strategy=delayed-retry-topic`:

```properties
mp.messaging.incoming.reservation-confirmed-in.failure-strategy=delayed-retry-topic
mp.messaging.incoming.reservation-confirmed-in.delayed-retry-topic.topics=reservation-confirmed-retry_1000,reservation-confirmed-retry_5000,reservation-confirmed-retry_15000
mp.messaging.incoming.reservation-confirmed-in.delayed-retry-topic.max-retries=3
mp.messaging.incoming.reservation-confirmed-in.delayed-retry-topic.timeout=30000
mp.messaging.incoming.reservation-confirmed-in.dead-letter-queue.topic=reservation-confirmed-dlq
```

Por que tópico de retry em vez de espera na pipeline: cada tentativa vira um **registro
persistente** com timestamp próprio. Dá para inspecionar "quantas vezes e quando", e o
backpressure entre tentativas é natural (tópico cheio = consumidor lento).

### 3.2 Semântica com o inbox

O retry precisa respeitar o claim:

```text
attempt 1 → claim = true → falha → NACK → rollback (claim liberado) → retry
attempt 2 → claim = true (de novo) → falha → NACK → rollback → retry
attempt 3 → claim = true → sucesso → ACK → claim permanece
```

Se o claim **não** fosse liberado no rollback, a tentativa 2 encontraria o evento "já
processado" e seria descartada — retry que nunca acontece. É por isso que a ADR 007 é
pré-requisito da 002, e não o contrário.

### 3.3 Eventos corruptos

Um payload sem `eventId` não pode ser claimado. O consumer falha na deserialização e, a
partir daí, segue o mesmo caminho: retry → DLQ.

```text
payload sem eventId → não há claim → consumer falha → NACK → retry_50/_100/_200 → DLQ
```

⚠️ **Correção factual:** não existe nenhuma classe `*Decorator*` no código — o
`IdempotencyMessagingDecorator` foi removido, e hoje `KafkaVehicleRegisteredConsumer`
desserializa **inline** (linhas 36-39). Não descreva um decorator que não existe.

🔴 **Sobre a evidência deste caminho:** `DlqKafkaIntegrationTest.shouldSendCorruptRecordToDeadLetterAfterRetriesAreExhausted`
**não prova o caminho de corrupção**. O `DlqTestConsumer` **sempre** faz `nack(...)` com uma
mensagem fixa, sem nunca chamar `KafkaVehicleRegisteredConsumer.deserialize`, e roda no
canal sintético `dlq-test-in`. O que ele prova é a **estratégia do SmallRye**
(`delayed-retry-topic` + `dead-letter-queue`) em canal controlado.

O que existe hoje para o consumer real:
`KafkaVehicleRegisteredConsumerTest.shouldFailWhenPayloadCannotBeDeserialized` (JUnit puro,
sem retry) e `DlqKafkaIntegrationTest.shouldConfigureDeadLetterTopicOnRealVehicleRegisteredChannel`
(igualdade de `@ConfigProperty`).

📌 **Dívida real:** falta um teste que produza um payload inválido no canal **real**
`vehicle-registered-in` e comprove retry → DLQ. Enquanto não existir, ADR 002 e ADR 003 têm
evidência de **estratégia**, não de **comportamento no canal de produção**.

## 4. DLQ (ADR 003)

### 4.1 O que o projeto descobriu

Antes da ADR 003, com `max-retries=3` esgotado, o conector **abandonava** o registro:

```text
SRMSG18280: delayedRetryNoDlq — the record is dropped, nothing to recover
```

Ou seja, o retry existia mas o **destino final** não. Um evento perdido sem rastro é pior
que uma falha barulhenta.

### 4.2 A decisão

```properties
mp.messaging.incoming.*-in.dead-letter-queue.topic=*-dlq
```

Depois do esgotamento, o registro vai para `<channel>-dlq`, com os headers de contagem de
tentativas preservados.

Teste que garante que a configuração **real** está no lugar (não só no canal de teste):

```java
@ConfigProperty(name = "mp.messaging.incoming.vehicle-registered-in.dead-letter-queue.topic")
String realChannelDeadLetterTopic;

@Test
void shouldConfigureDeadLetterTopicOnRealVehicleRegisteredChannel() {
    assertEquals("vehicle-registered-dlq", realChannelDeadLetterTopic, "...");
}
```

Esse teste é barato e pega regressão de configuração — a classe mais perigosa de bug em
infra de messaging, porque a suíte continua verde enquanto o broker real perde mensagens.

## 5. Outbox transacional (ADR 004 / 008)

### 5.1 A janela

```text
persiste estado  →  publica no Kafka
      ↑                ↑
  commit ok        kafka fora do ar
                    ⇒ estado existe, evento nunca existiu
```

Fazer `DB transaction + Kafka publish` como **uma** transação distribuída (XATCC) está fora
do escopo deste laboratório. A meta é uma fronteira **local, durável e observável**.

### 5.2 A decisão

```text
        LOCAL TRANSACTION
        ┌─────────┴─────────┐
   Business state     Outbox event
        └─────────┬─────────┘
              COMMIT
                 ↓
         Outbox publisher (relay)
                 ↓
               Kafka
```

Garantias assumidas (e explicitamente **não** além delas):

- se o estado foi confirmado, o registro da outbox também foi;
- se a transação falhou, os dois voltaram;
- se o Kafka está fora, o evento fica persistido para nova tentativa;
- **publicação duplicada é possível** e precisa ser tratada pelo consumidor com idempotência;
- a outbox **não** promete exactly-once global.

Tabela da outbox:

| Coluna | Papel |
|---|---|
| `event_id` | identificador único e idempotente |
| `event_type` | tipo lógico do contrato |
| `aggregate_type` | tipo do aggregate produtor |
| `aggregate_id` | identificador do aggregate → **chave do Kafka** |
| `payload` | representação serializada |
| `occurred_at` | quando o evento foi criado |
| `published_at` | quando a publicação foi confirmada (null = pendente) |
| `attempts` | tentativas de publicação |

### 5.3 O relay

```java
@Scheduled(every = "5s", concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
Uni<Void> relay() { return publisher.handle(); }
```

`ConcurrentExecution.SKIP` evita sobreposição: sem isso, dois relays poderiam publicar o
mesmo registro. E `%test.quarkus.scheduler.enabled=false` desliga o scheduler no teste, para
o teste controlar o momento da publicação.

### 5.4 A chave do Kafka

```java
return emitter.sendMessage(Message.of(Record.of(event.aggregateId(), event.payload())));
```

`aggregateId` é o `invoiceId`. Efeito: **todos os eventos do mesmo agregado caem na mesma
partição**, logo são consumidos **em ordem**. Isso é o que permite reconstruir a sequência
`DRAFT → OPEN` sem estado extra no consumidor.

Teste que trava esse contrato:

```java
assertEquals(invoiceId.get(), record.key(), "o outbox record must be keyed by the invoice id");
```

## 6. Por que Kafka e não RabbitMQ

O capítulo 9 mostra os dois. A escolha do projeto:

| Necessidade | Kafka | RabbitMQ |
|---|---|---|
| ordenação por chave (partição) | ✅ nativa | ❌ (exige exchange por agregado ou consistência explícita) |
| histórico para retry/DLQ | ✅ tópicos | ⚠️ DLX, sem histórico |
| consumer group | ✅ nativo | ⚠️ via exchange/fila, sem rebalanceamento equivalente |
| transação distribuída (se necessário) | ✅ | ❌ |

RabbitMQ é melhor quando o caso é **roteamento** (exchange, routing key, prioridade). O
caso do projeto é **ordem por agregado + redelivery controlável**, que é o DNA do Kafka.

## 7. Checklist de estudo

- [ ] Sei descrever a janela que a ADR 007 fechou, com desenho antes/depois.
- [ ] Sei explicar por que o claim precisa ser liberado no rollback.
- [ ] Sei dizer o que `SRMSG18280` significa e por que a DLQ era obrigatória.
- [ ] Sei listar as garantias da outbox **e** o que ela **não** garante.
- [ ] Sei explicar por que `aggregateId` é a chave Kafka.
- [ ] Sei justificar Kafka sobre RabbitMQ para este domínio.
- [ ] Sei explicar o papel de `ConcurrentExecution.SKIP`.

**Veja também:** [08-messaging-reativo.md](./08-messaging-reativo.md) ·
[04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md)

---

_Última atualização: 2026-09-26 (cap. 9 + ADRs 002/003/004/005/007/008)._
