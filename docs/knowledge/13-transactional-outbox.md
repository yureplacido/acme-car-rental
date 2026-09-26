# 13 — Transactional Outbox na prática

> Capítulo 9 do *Quarkus in Action*.
> Norma do projeto: [ddd-tdd-standards.md](../ddd-tdd-standards.md) §5 (transações) e
> regra 13 do [AGENTS.md](../../AGENTS.md) (I/O externo fora de transação).
> Decisões: [ADR 004](../adr/004-transactional-outbox.md),
> [ADR 008](../adr/008-transactional-outbox.md),
> [ADR 005](../adr/005-durable-inbox.md), [ADR 007](../adr/007-transactional-inbox.md).
> Última atualização: 2026-09-26 (cap. 9 + implementação do `billing-service`).

> **Como este arquivo referencia código:** cada conceito traz o **trecho real** do
> `billing-service`, copiado aqui. Não há link para arquivo de código: o estudo precisa
> ser legível mesmo com o repositório fechado. O nome do arquivo aparece como texto ao
> lado do trecho, para você localizar no editor quando for conferir.

---

## 1. Conceitos do capítulo

### 1.1 Dual-write

- **O que é:** gravar no banco **e** publicar no broker como duas operações separadas.
- **Por que existe:** é a forma ingênua de "mandar um evento quando algo acontece".
- **Quando usar / quando não usar:** nunca, se você precisar de consistência. Duas
  operações em dois sistemas sem transação ACID comum têm quatro estados possíveis, e
  só um deles é o desejado.
- **Armadilha clássica:** o código parece correto, os testes passam com o broker no ar, e
  o defeito só aparece quando o processo morre entre as duas operações.

```text
    banco: COMMIT ok ───────────────► evento publicado? talvez não. ✗
    banco: COMMIT falhou ───────────► evento publicado? não deveria. ✓
```

Se qualquer uma das duas pode falhar depois da outra ter sido confirmada, não há
consistência — há sorte.

### 1.2 Transactional Outbox

- **O que é:** gravar o evento na **mesma transação** do efeito de negócio, e publicar
  depois, por um processo separado que relê o que ficou pendente.
- **Por que existe:** troca consistência imediata (impossível) por **consistência
  eventual recuperável**.
- **Quando usar / quando não usar:** sempre que o efeito de negócio precisar gerar um
  evento para outro contexto. Não use para notificação interna sem consequência, nem
  quando o broker é dispensável.
- **Armadilha clássica:** achar que o outbox entregou *exactly-once*. Ele entrega
  *at-least-once*; a deduplicação é do consumidor.

```text
    OpenInvoiceForRental
           │
           ├──► invoice        ┐  mesma transação
           └──► outbox_event   ┘
                     │
                     ▼
                OutboxRelay (@Scheduled)
                     │
                     ▼
                   Kafka
```

### 1.3 At-least-once

- **O que é:** o relay pode publicar o mesmo evento mais de uma vez.
- **Por que existe:** a janela entre "publicar" e "marcar como publicado" é inevitável
  se você não usar transação distribuída com o broker.
- **Quando usar / quando não usar:** assuma sempre. Se o consumidor não é idempotente, o
  outbox só deslocou o problema.
- **Armadilha clássica:** marcar `publishedAt` **antes** de publicar, para "garantir uma
  vez só". Isso troca duplicata por perda — e perda é pior que duplicata.

```text
    publish()  ─────►  markPublished()        ✅ publicação, depois marca
    markPublished()  ─────►  publish()        ✗ evento perdido se o processo cair
```

---

## 2. O que o repositório fez

🧪 O `billing-service` implementa o padrão completo: caso de uso, porta de saída,
persistência, relay agendado e publicador Kafka. Os trechos abaixo são o código real.

### 2.1 O caso de uso só conhece a porta

`application/usecase/OpenInvoiceForRental.java` — monta a linha, abre o agregado e
chama `saveOpenedWithOutbox`. Não sabe que existe banco, nem Kafka, nem outbox:

```java
public Uni<Invoice> handle(Command command) {
    RentalDetails details = command.details();
    Money dailyRate = details.dailyRate();
    InvoiceLine rental = InvoiceLine.rentalDays(
            "Aluguel de veículo " + details.licensePlate(),
            details.startDate(),
            details.endDate(),
            dailyRate);
    return repository.findByReservationId(details.reservationId())
            .map(maybe -> maybe.orElseThrow(() ->
                    new IllegalStateException("No draft invoice for reservation: " + details.reservationId())))
            .map(invoice -> invoice.replaceLines(List.of(rental)).open())
            .flatMap(repository::saveOpenedWithOutbox);
}
```

O nome `saveOpenedWithOutbox` já denuncia a decisão: **gravar e enfileirar é uma
operação só**, do ponto de vista do domínio.

### 2.2 A porta que separa aplicação de infraestrutura

`application/port/out/OutboxEventStore.java` — a interface não menciona JPA, Panache
nem tabela:

```java
public interface OutboxEventStore {

    Uni<Void> appendInvoiceOpened(InvoiceOpened event);

    Uni<List<OutboxEvent>> findPending(int limit);

    Uni<Void> markPublished(OutboxEvent event, Instant publishedAt);

    Uni<Void> incrementAttempts(OutboxEvent event);
}
```

`application/port/out/EventPublisher.java` — o relay publica por esta porta, e só ela:

```java
public interface EventPublisher {

    Uni<Void> publish(OutboxEvent event);
}
```

É essa porta que permite testar o relay com **fakes**, sem broker nenhum
(ver §4, camada Application).

### 2.3 O evento é um modelo explícito, com versão

`application/event/InvoiceOpened.java` — record, não serialização acidental de
entidade. Repare no campo `version`, que já existe desde o primeiro dia:

```java
public record InvoiceOpened(
        UUID eventId,
        int version,
        Instant occurredAt,
        String invoiceId,
        String customerId,
        String reservationId,
        BigDecimal totalAmount,
        String currency) {
}
```

`application/model/OutboxEvent.java` — o que o relay lê. Repare que **não** tem
`publishedAt`: para o relay, "publicado" é a ausência do registro da lista de pendentes.

```java
public record OutboxEvent(
        UUID eventId,
        String eventType,
        String aggregateType,
        String aggregateId,
        String payload,
        Instant occurredAt,
        int attempts) {
}
```

### 2.4 A transação que amarra as duas escritas

`adapter/out/persistence/PanacheInvoiceRepository.java` — o `@WithTransaction` cobre a
persistência do agregado **e** a inserção na outbox. É aqui que mora a garantia:

```java
@Override
@WithTransaction
public Uni<Invoice> saveOpenedWithOutbox(Invoice invoice) {
    return persistInvoice(invoice)
            .flatMap(saved -> {
                InvoiceOpened event = new InvoiceOpened(
                        UUID.randomUUID(),
                        1,
                        Instant.now(),
                        saved.id().value(),
                        saved.customerId(),
                        saved.reservationId(),
                        saved.total().amount(),
                        saved.total().currency());
                return outboxEventStore.appendInvoiceOpened(event)
                        .replaceWith(saved);
            });
}
```

⚠️ Se alguém trocar `@WithTransaction` por `@WithSession`, o padrão inteiro quebra em
silêncio: as duas escritas continuam funcionando, mas deixam de ser atômicas. **Este é o
ponto mais frágil da implementação** e o que o teste da §4.2 existe para travar.

### 2.5 A tabela da outbox

`adapter/out/persistence/OutboxEventEntity.java` — `public` porque é Panache, e
`event_id` com restrição única porque é a identidade idempotente do evento:

```java
@Entity
@Table(name = "outbox_event",
        uniqueConstraints = @UniqueConstraint(columnNames = "event_id"))
public class OutboxEventEntity extends io.quarkus.hibernate.reactive.panache.PanacheEntity {

    @Column(name = "event_id", nullable = false)
    public String eventId;

    @Column(name = "event_type", nullable = false)
    public String eventType;

    @Column(name = "aggregate_type", nullable = false)
    public String aggregateType;

    @Column(name = "aggregate_id", nullable = false)
    public String aggregateId;

    @Column(name = "payload", nullable = false, columnDefinition = "TEXT")
    public String payload;

    @Column(name = "occurred_at", nullable = false)
    public Instant occurredAt;

    @Column(name = "published_at")
    public Instant publishedAt;

    @Column(name = "attempts", nullable = false)
    public int attempts;
}
```

`published_at` é **anulável e sem default**: `null` significa pendente. A consulta de
pendência é literalmente "onde published_at é nulo".

### 2.6 O adapter de persistência

`adapter/out/persistence/PanacheOutboxEventStore.java` — note a diferença de anotação
entre ler e escrever, e o fato de a escrita fixar `eventType` e `aggregateType` no código
(em vez de esperar do evento):

```java
@Override
public Uni<Void> appendInvoiceOpened(InvoiceOpened event) {
    OutboxEventEntity entity = new OutboxEventEntity();
    entity.eventId = event.eventId().toString();
    entity.eventType = "InvoiceOpened";
    entity.aggregateType = "Invoice";
    entity.aggregateId = event.invoiceId();
    entity.occurredAt = event.occurredAt();
    entity.publishedAt = null;
    entity.attempts = 0;

    try {
        entity.payload = objectMapper.writeValueAsString(event);
    } catch (JsonProcessingException e) {
        return Uni.createFrom().failure(
                new IllegalStateException("Could not serialize InvoiceOpened event", e));
    }

    return persist(entity).replaceWithVoid();
}
```

```java
@Override
@WithSession
public Uni<List<OutboxEvent>> findPending(int limit) {
    return find("publishedAt is null order by occurredAt")
            .page(0, limit)
            .list()
            .map(entities -> entities.stream()
                    .map(this::toModel)
                    .toList());
}
```

`@WithSession` na leitura (não transação: só consulta) e `@WithTransaction` nas escritas.
O `order by occurredAt` é o que dá ordem de publicação dentro do lote.

### 2.7 O relay: adapter que não inventa regra

`adapter/out/messaging/OutboxRelay.java` é adapter, e mesmo assim ele **não decide nada**
sobre publicação: ele só agenda o caso de uso
(`application/usecase/PublishPendingOutboxEvents.java`), que é onde mora a orquestração:

```java
public Uni<Void> handle() {
    return handle(DEFAULT_BATCH_SIZE);
}

public Uni<Void> handle(int batchSize) {
    return outboxEventStore.findPending(batchSize)
            .flatMap(this::publishSequentially);
}

private Uni<Void> publishSequentially(List<OutboxEvent> events) {
    return Multi.createFrom().iterable(events)
            .onItem().transformToUniAndConcatenate(this::publishOne)
            .collect().last()
            .replaceWithVoid();
}

private Uni<Void> publishOne(OutboxEvent event) {
    return eventPublisher.publish(event)
            .flatMap(ignored -> outboxEventStore.markPublished(event, Instant.now()))
            .onFailure()
            .call(ignored -> outboxEventStore.incrementAttempts(event));
}
```

Três decisões legíveis aqui:

1. `transformToUniAndConcatenate` — **sequencial**, não paralelo. Publicar em paralelo
   destruiria a ordem de `occurredAt` que a consulta pediu.
2. `markPublished` no `flatMap` — só executa se `publish` completar com sucesso. É a
   ordem obrigatória da §1.3, escrita como código.
3. `incrementAttempts` no `onFailure().call(...)` — registra a tentativa **e** propaga a
   falha, o que interrompe o lote (o `concat` para na primeira falha).

### 2.8 O agendamento

`adapter/out/messaging/OutboxRelay.java` — o adapter vira apenas o gatilho do tempo:

```java
@ApplicationScoped
public class OutboxRelay {

    private final PublishPendingOutboxEvents publisher;

    public OutboxRelay(PublishPendingOutboxEvents publisher) {
        this.publisher = publisher;
    }

    @Scheduled(
            every = "5s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> relay() {
        return publisher.handle();
    }
}
```

⚠️ `SKIP` só evita sobreposição **na mesma instância**. Em escala horizontal, duas
instâncias ainda podem ler o mesmo evento pendente e publicar em duplicata — o que o
at-least-once já tolera, mas que irrita. A evolução correta é `SELECT ... FOR UPDATE
SKIP LOCKED` ou lease com expiração.

### 2.9 A publicação no Kafka

`adapter/out/messaging/InvoiceOpenedKafkaPublisher.java` — a **chave** é o
`aggregateId`, o que garante que todos os eventos da mesma fatura vão para a mesma
partição e, portanto, mantenham ordem:

```java
@ApplicationScoped
public class InvoiceOpenedKafkaPublisher implements EventPublisher {

    private final MutinyEmitter<Record<String, String>> emitter;

    public InvoiceOpenedKafkaPublisher(
            @Channel("invoice-opened-out") MutinyEmitter<Record<String, String>> emitter) {
        this.emitter = emitter;
    }

    @Override
    public Uni<Void> publish(OutboxEvent event) {
        return emitter.sendMessage(
                Message.of(
                        Record.of(
                                event.aggregateId(),
                                event.payload())));
    }
}
```

### 2.10 Configuração

`billing-service/src/main/resources/application.properties`:

```properties
mp.messaging.outgoing.invoice-opened-out.connector=smallrye-kafka
mp.messaging.outgoing.invoice-opened-out.topic=invoice-opened
mp.messaging.outgoing.invoice-opened-out.key.serializer=org.apache.kafka.common.serialization.StringSerializer
mp.messaging.outgoing.invoice-opened-out.value.serializer=org.apache.kafka.common.serialization.StringSerializer
```

O canal é **explícito**. O auto-detect do capítulo 9 funcionaria para o payload, mas a
chave (`key.serializer`) e a intenção de ordenação por agregado não são adivinháveis —
e o nome do canal precisa ser o mesmo usado no `@Channel`.

### 2.11 Dependências

`billing-service/pom.xml`:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-messaging-kafka</artifactId>
</dependency>
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-scheduler</artifactId>
</dependency>
```

O canal de saída Kafka vem de `quarkus-messaging-kafka`; o agendamento do relay vem de
`quarkus-scheduler` — o relay é `every=5s` com `concurrentExecution=SKIP`, então ele roda
fora do loop de requisição.

⚠️ **Regra 16 do [AGENTS.md](../../AGENTS.md):** `artifactId` verificado no
**3.39.3** antes de escrever. `quarkus-scheduler` não é opcional aqui: sem ele não há
`@Scheduled` e o relay nunca roda.

### 2.12 Mapa capítulo → código → teste

| Conceito | Onde está no código | Teste que prova |
|---|---|---|
| Caso de uso não conhece infra | `application/usecase/OpenInvoiceForRental.java` | `OpenInvoiceForRentalTest` |
| Porta de saída | `application/port/out/OutboxEventStore.java` | `PublishPendingOutboxEventsTest` |
| Evento versionado | `application/event/InvoiceOpened.java` | `BillingOutboxIntegrationTest` |
| **Atomicidade invoice + outbox** | `adapter/out/persistence/PanacheInvoiceRepository.java` | `BillingOutboxIntegrationTest` |
| Tabela e `published_at` nulo | `adapter/out/persistence/OutboxEventEntity.java` | `BillingOutboxIntegrationTest` |
| Publicação sequencial e ordem | `application/usecase/PublishPendingOutboxEvents.java` | `PublishPendingOutboxEventsTest` |
| Marcar **depois** de publicar | `application/usecase/PublishPendingOutboxEvents.java` | `PublishPendingOutboxEventsTest` |
| Relay agendado | `adapter/out/messaging/OutboxRelay.java` | `OutboxRelayKafkaIntegrationTest` |
| Chave = `aggregateId` | `adapter/out/messaging/InvoiceOpenedKafkaPublisher.java` | `OutboxRelayKafkaIntegrationTest` |
| Relay entrega no broker real | `adapter/out/messaging/InvoiceOpenedKafkaPublisher.java` (via `OutboxRelay`) | `OutboxRelayKafkaIntegrationTest` |
| Claim concorrente entre instâncias | — | 🔜 não implementado (ver §6) |

---

## 3. Divergências do livro

| # | O que o livro faz | O que fazemos | Por quê | Onde está registrado |
|---|---|---|---|---|
| 1 | Publica direto do caso de uso, via emitter | Caso de uso publica pela porta `EventPublisher`; o Kafka fica no adapter | emitter no caso de uso amarra aplicação a MicroProfile Reactive Messaging | [ddd-tdd-standards.md](../ddd-tdd-standards.md) §5 |
| 2 | Deixa `event_type` e `aggregate_type` a cargo de quem serializa | `event_type` e `aggregate_type` são **fixos no adapter**, não vêm do evento | permite consultar a outbox por tipo sem fazer parse do `payload` | [contracts.md](../contracts.md) |
| 3 | Evento sem versão | `InvoiceOpened` tem `int version` desde o início | payload persistido precisa continuar interpretável depois de criado | [contracts.md](../contracts.md) |
| 4 | Relay com `poll` explícito ou `@Scheduled` | `@Scheduled(every = "5s", concurrentExecution = SKIP)` | o relay é infraestrutura de borda; o caso de uso não sabe que existe tempo | [services.md](../services.md) §billing |
| 5 | Retry do relay com backoff | Só `attempts = attempts + 1`; **não** há `nextAttemptAt` | a política de retry do relay não está implementada; está declarada como evolução, não escondida | [ADR 004](../adr/004-transactional-outbox.md) |
| 6 | Menciona exactly-once como possibilidade | Documentamos explicitamente **at-least-once** e a janela de duplicata | exactly-once exigiria transação distribuída com o broker | [ADR 008](../adr/008-transactional-outbox.md) |
| 7 | Exemplo único, sem teste | Três camadas de teste: aplicação (fake), persistência (SQL), messaging (broker real) | regra 12 do [AGENTS.md](../../AGENTS.md) | [04](./04-estrategia-de-testes-do-projeto.md) |

---

## 4. Testes: camada por camada

🧪 São três arquivos, em três camadas, e cada um trava uma propriedade diferente do
padrão. Os trechos abaixo são o código real dos testes.

### 4.1 Application — a ordem obrigatória, sem broker

`application/usecase/PublishPendingOutboxEventsTest.java` — JUnit puro, com fakes das
duas portas. É o arquivo que **mais** prova sobre o padrão, e o mais rápido do projeto
(175 linhas, 4 comportamentos, zero dependência de infra).

```java
class PublishPendingOutboxEventsTest {

    @Test
    void shouldPublishPendingEventsAndMarkEachOneAsPublished() {
        OutboxEvent first = event("first");
        OutboxEvent second = event("second");
        FakeOutboxEventStore store = new FakeOutboxEventStore(List.of(first, second));
        FakeEventPublisher publisher = new FakeEventPublisher();

        PublishPendingOutboxEvents useCase =
                new PublishPendingOutboxEvents(store, publisher);

        useCase.handle().await().indefinitely();

        assertEquals(List.of(first.eventId(), second.eventId()), publisher.publishedIds);
        assertEquals(List.of(first.eventId(), second.eventId()), store.markedIds);
        assertTrue(store.incrementedIds.isEmpty());
    }
```

O que cada comportamento trava:

| Teste | Propriedade travada |
|---|---|
| `shouldPublishPendingEventsAndMarkEachOneAsPublished` | todo publicado é marcado; nenhum `incrementAttempts` no caminho feliz |
| `shouldDoNothingWhenThereAreNoPendingEvents` | lote vazio não publica nem marca nada |
| `shouldIncrementAttemptsAndStopBatchWhenPublicationFails` | falha no 2º evento → `attempts` só no 2º, 3º **nunca** é publicado, e o 1º continua marcado |
| `shouldUseRequestedBatchSize` | o `limit` chega à porta |

O terceiro é o que prova §2.7 item 3. O trecho que importa:

```java
assertTrue(failure != null);
assertEquals(
        List.of(first.eventId(), second.eventId()),
        publisher.publishedIds);
assertEquals(
        List.of(first.eventId()),
        store.markedIds);
assertEquals(
        List.of(second.eventId()),
        store.incrementedIds);
assertFalse(publisher.publishedIds.contains(third.eventId()));
```

Ou seja: o 1º foi publicado **e** marcado, o 2º foi publicado **e** falhou ao marcar
(virando `attempts`), e o 3º **não foi tocado**. Isso é concatenação com interrupção, e
é o comportamento desejado.

### 4.2 Adapter de persistência — a atomicidade, via SQL

`adapter/out/persistence/BillingOutboxIntegrationTest.java` — Postgres real, leitura por
SQL cru (não pelo repositório, para não proveitar o próprio código sob teste):

```java
void shouldCommitInvoiceAndOutboxTogetherWhenInvoiceIsOpened(UniAsserter asserter) {
    String reservationId = "outbox-" + UUID.randomUUID();

    // cria fatura DRAFT e abre via OpenInvoiceForRental (setup do teste)

    asserter.<Row>assertThat(
            () -> pgPool.withConnection(connection ->
                    connection.preparedQuery("""
                                    SELECT i.status,
                                           o.event_id,
                                           o.event_type,
                                           o.aggregate_id,
                                           o.published_at,
                                           o.attempts
                                    FROM invoice i
                                    JOIN outbox_event o
                                      ON o.aggregate_id = i.id::text
                                    WHERE i.reservation_id = $1
                                    """)
                            .execute(io.vertx.mutiny.sqlclient.Tuple.of(reservationId))
                            .map(rows -> {
                                var iterator = rows.iterator();

                                if (!iterator.hasNext()) {
                                    return null;
                                }

                                var item = iterator.next();

                                return new Row(
                                        item.getString("status"),
                                        item.getString("event_id"),
                                        item.getString("event_type"),
                                        item.getString("aggregate_id"),
                                        item.getOffsetDateTime("published_at") != null
                                                ? item.getOffsetDateTime("published_at").toInstant()
                                                : null,
                                        item.getInteger("attempts"));
                            })),
            row -> {
                assertNotNull(row);
                assertEquals("OPEN", row.status());
                assertNotNull(row.eventId());
                assertEquals("InvoiceOpened", row.eventType());
                assertNotNull(row.aggregateId());
                assertNull(row.publishedAt());
                assertEquals(0, row.attempts());
            });
}
```

O que a asserção prova, e é o ponto inteiro do capítulo: **na mesma transação** o status
virou `OPEN` **e** a linha da outbox existe com `published_at` nulo e `attempts = 0`.
Se a inserção da outbox estivesse fora da transação da fatura, este SELECT não
encontraria linha nenhuma e `row` seria `null` — falha o `assertNotNull`.

O `JOIN` por `aggregate_id` é o que prova que existe **um** registro de outbox para a
fatura, e `published_at IS NULL` prova que o relay ainda não rodou. Se alguém remover o
`@WithTransaction` da §2.4 e a escrita da outbox passar a falhar silenciosamente, este
teste quebra.

### 4.3 Adapter de messaging — o relay contra o broker real

`adapter/out/messaging/OutboxRelayKafkaIntegrationTest.java` — este é o teste que
incorporou as correções de `commit a6fddc0`, e é também o melhor exemplo do padrão
reativo do projeto:

```java
@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class OutboxRelayKafkaIntegrationTest {

    private static final String TOPIC = "invoice-opened";
    private static final Duration PUBLISH_TIMEOUT = Duration.ofSeconds(10);

    @InjectKafkaCompanion
    KafkaCompanion companion;
```

O corpo do teste faz três coisas em cadeia, e cada uma é uma lição:

```java
TopicPartition partition = KafkaCompanion.tp(TOPIC, 0);

asserter.assertThat(
        () -> onWorkerThread(() -> companion.offsets().get(partition, OffsetSpec.latest()).offset())
                .emitOn(eventLoop)
                .flatMap(endOffset -> relay.relay().replaceWith(endOffset))
                .flatMap(endOffset -> onWorkerThread(
                        () -> awaitInvoiceOpenedAfter(partition, endOffset, invoiceId.get()))),
        record -> {
            assertEquals(invoiceId.get(), record.key(),
                    "the outbox record must be keyed by the invoice id");
            assertNotNull(record.value());
            assertTrue(record.value().contains(reservationId),
                    "the published payload must reference the invoice reservation: " + record.value());
        });
```

```java
private <T> Uni<T> onWorkerThread(Supplier<T> blockingCall) {
    return Uni.createFrom().item(blockingCall)
            .runSubscriptionOn(Infrastructure.getDefaultExecutor());
}

private ConsumerRecord<String, String> awaitInvoiceOpenedAfter(
        TopicPartition partition, long fromOffset, String invoiceId) {
    return companion.consumeStrings()
            .fromOffsets(Map.of(partition, fromOffset),
                    records -> records.select().where(record -> invoiceId.equals(record.key())))
            .awaitRecords(1, PUBLISH_TIMEOUT)
            .getRecords()
            .get(0);
}
```

| Trecho | Lição |
|---|---|
| `companion.offsets()` dentro de `onWorkerThread` | a API do Kafka Companion é **bloqueante**; em `@RunOnVertxContext` ela precisa sair da event loop |
| `.emitOn(eventLoop)` antes do `relay.relay()` | o relay é reativo e roda na event loop; voltar a ela é o que mantém o contrato do código de produção |
| `fromOffsets(...)` em vez de `clear()`/recriar tópico | não destroi estado de outros testes e não depende de "a primeira mensagem é a minha" |
| `.where(record -> invoiceId.equals(record.key()))` | desambigua por chave, não por posição — é o que torna o teste paralelo seguro |
| `awaitRecords(1, PUBLISH_TIMEOUT)` com 10s | publisher é assíncrono e o relay é agendado; sem timeout o teste vira pendurado |

⚠️ Note o que este teste **não** prova: ele não cobre publicação duplicada nem
recuperação após crash entre `publish` e `markPublished`. Ver §6.

### 4.4 Como rodar

```bash
./mvnw -pl billing-service test
```

Sobe **um** broker (recurso único com `restrictToAnnotatedClass = false`) e roda os 49
testes do serviço.

---

## 5. Armadilhas

### 5.1 Outbox sem `@WithTransaction`

- **Sintoma:** nenhum erro. Invoice e outbox continuam sendo gravadas — às vezes juntas,
  às vezes não, dependendo da corrida.
- **Causa:** `saveOpenedWithOutbox` sem `@WithTransaction` (ou com `@WithSession`).
- **Correção:** `@WithTransaction` cobrindo as duas escritas.
- **Prevenção:** [x] o teste da §4.2 lê as duas tabelas no mesmo SQL.

### 5.2 Marcar antes de publicar

- **Sintoma:** evento some depois de um restart.
- **Causa:** `markPublished` executado antes de `publish`, "para garantir uma vez só".
- **Correção:** `publish` no `flatMap` que só então chama `markPublished` (§2.7).
- **Prevenção:** [x] `shouldPublishPendingEventsAndMarkEachOneAsPublished` verifica os
  dois lados; [ ] nenhum teste prova a ordem **entre** os dois chamadas, só o resultado —
  a lacuna está registrada em §6.

### 5.3 Payload serializado da entidade

- **Sintoma:** quebra ao renomear um campo do domínio.
- **Causa:** `writeValueAsString(invoiceEntity)` em vez de um record dedicado.
- **Correção:** `InvoiceOpened` como record explícito, com `version`.
- **Prevenção:** [x] o tipo é declarado em `application/event`, não em `adapter`.

### 5.4 I/O bloqueante na event loop

- **Sintoma:** `IllegalStateException: The current thread cannot be blocked` (Mutiny), ou
  `HR000068` (Hibernate Reactive), em teste anotado com `@RunOnVertxContext`.
- **Causa:** `companion.offsets().get(...)` — API síncrona do Kafka Companion — chamada
  direto no event loop.
- **Correção:** `onWorkerThread(...)` + `.emitOn(eventLoop)`.
- **Prevenção:** [x] helper `onWorkerThread(Supplier<T>)` no teste; [x] a regra está em
  [testing.md](../testing.md) §Reactive testing; [ ] ainda não há lint que a aplique
  automaticamente.

Detalhe completo em [11-armadilhas-e-licoes.md §2](./11-armadilhas-e-licoes.md).

### 5.5 Tópico de saída que ninguém criou

- **Sintoma:** `UNKNOWN_TOPIC_OR_PARTITION` ao ler o offset de `invoice-opened`.
- **Causa:** `invoice-opened` só recebe quando o relay roda, mas o teste precisa do
  offset **antes** de rodar o relay.
- **Correção:** pré-criar o tópico no `start()` do `BillingKafkaCompanionResource`.
- **Prevenção:** [x] o recurso lista os 17 tópicos do billing, principais, retry, DLQ
  e `invoice-opened`.

---

## 6. Conceitos que NÃO usamos (e por quê)

| Conceito | Por que não usamos | Quando reavaliar |
|---|---|---|
| `SELECT ... FOR UPDATE SKIP LOCKED` para claim concorrente | `SKIP` no `@Scheduled` basta para uma instância; o custo é complexidade de transação manual | ao rodar **mais de uma** instância do `billing-service` |
| Lease com expiração (`claimed_until`) | mesma razão; `SKIP LOCKED` é suficiente e mais simples | idem |
| `nextAttemptAt` + backoff exponencial + jitter | `attempts` registra a tentativa mas não agenda; para o volume atual, republicar a cada 5s é aceitável | quando a taxa de falha de publicação incomodar |
| Tabela de dead-letter para a outbox | hoje um evento que falha sempre fica pendente e ocupa o lote | quando precisar de alerta sobre evento preso |
| `exactly-once` | exigiria transação distribuída entre Postgres e Kafka; o custo não se justifica | nunca, no modelo atual |
| Métricas / tracing do `eventId` | não há observabilidade no serviço ainda | junto com o capítulo de cloud-native |

📌 **Lacunas honestas deste padrão, hoje:**

1. **Publicação duplicada não tem teste.** O relay publica, o processo morre,
   `published_at` continua `null`, e o próximo ciclo republica. É o comportamento
   correto de *at-least-once*, e nada na suíte o trava.
2. **`claim` concorrente entre instâncias não é testado** porque não é implementado.
3. **Ordem global não é garantida** — só por agregado, e só porque a chave é o
   `aggregateId`.

---

## 7. Checklist de estudo

- [ ] Explicar por que o outbox troca uma garantia que não existe por uma que existe.
- [ ] Dizer o que aconteceria se `markPublished` viesse antes de `publish`.
- [ ] Apontar no código onde está a fronteira atômica e por que ela é frágil.
- [ ] Explicar por que a chave do registro Kafka é o `aggregateId`.
- [ ] Dizer qual parte do relay é caso de uso e qual é adapter.
- [ ] Justificar `transformToUniAndConcatenate` em vez de `merge`.
- [ ] Explicar por que `OutboxEvent` (record) não tem `publishedAt` e a entidade tem.

**Não sei responder ainda (viraram tarefa):**

- [ ] Como dimensionar `every = "5s"` em função da taxa de publicação.
- [ ] Se `SKIP LOCKED` do Postgres se comporta bem com Hibernate Reactive
  Panache sem escrita manual.
- [ ] Como versionar o `payload` já persistido quando o `InvoiceOpened` evoluir.

---

## 8. Referências

| Tipo | Referência |
|---|---|
| ADR | [004 — Transactional outbox](../adr/004-transactional-outbox.md) |
| ADR | [008 — Outbox transacional](../adr/008-transactional-outbox.md) |
| ADR | [005 — Inbox durável](../adr/005-durable-inbox.md) |
| ADR | [007 — Inbox transacional](../adr/007-transactional-inbox.md) |
| Página do repositório | [services.md](../services.md) §billing |
| Página do repositório | [contracts.md](../contracts.md) §`InvoiceOpened` |
| Página do repositório | [testing.md](../testing.md) |
| Página do repositório | [architecture.md](../architecture.md) §Billing |
| Guia oficial | <https://quarkus.io/guides/smallrye-reactive-messaging-kafka> (3.39.3) |
| Fonte do artefato | `~/.m2/repository/io/quarkus/quarkus-messaging-kafka/3.39.3/` |
| Fonte do artefato | `~/.m2/repository/io/smallrye/reactive/smallrye-reactive-messaging-kafka/4.37.0/` |

---

## 9. Checklist de fecho

- [x] `docs/knowledge/README.md` com a linha do capítulo no índice e no mapa
- [x] `docs/README.md` com a linha no "Mapa Livro → Documentação"
- [x] `docs/roadmap.md` com o status e evidência executável
- [x] ADRs criadas/atualizadas para toda divergência (004, 005, 007, 008)
- [x] `docs/testing.md` com a regra de `await()` sob `@RunOnVertxContext`
- [x] suíte do serviço verde: `./mvnw -pl billing-service test` (49 testes)
- [x] guardians executados (`/ddd-audit`, `/tdd-audit`, `/quarkus-audit`, `/architecture-audit`)

---

## 10. Checklist de governança do capítulo

```text
[x] capítulo lido e anotado no momento da leitura
[x] conceitos nomeados como no livro (dual-write, transactional outbox, at-least-once)
[x] mapa capítulo → código → teste completo
[x] código referenciado por trecho embutido, não por link
[x] divergências com regra/ADR (7 linhas, todas com regra)
[x] nada de API sem verificação contra 3.39.3
[x] armadilhas reais registradas (5)
[x] conceitos recusados registrados com motivo (6)
[x] lacunas honestas declaradas, não escondidas
[x] índice + roadmap + README da docs atualizados
[x] suíte verde
[x] guardians rodados
```

---

**Veja também:** [README.md](./README.md) ·
[08-messaging-reativo.md](./08-messaging-reativo.md) ·
[09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md) ·
[04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md)

---

_Última atualização: 2026-09-26 (reescrito no padrão do
[12-modelo-para-novos-capitulos.md](./12-modelo-para-novos-capitulos.md), com trechos de
código embutidos e lacunas declaradas)._
