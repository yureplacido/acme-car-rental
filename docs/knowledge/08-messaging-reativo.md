# 08 — Messaging reativo

> Capítulo 9 do *Quarkus in Action*.
> Decisões de resiliência: [09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md).
> Contratos: [contracts.md](../contracts.md).

## 1. O que o livro ensina

O capítulo 9 introduz **MicroProfile Reactive Messaging (MP-RM)**, a implementação SmallRye
e dois connectors (Kafka e RabbitMQ). O conceito central é o **canal**:

```text
produtor  →  canal  →  [processadores]  →  consumidor
```

Uma regra estrutural que o livro deixa explícita e que é fácil de violar:

> Um canal tem **um** produtor e **um** consumidor, com qualquer número de processadores no
> meio. Processadores também se comunicam por canais **únicos**, e é a ordem dos canais que
> define a ordem de execução.

O SmallRye (implementação no Quarkus) **estende** o que a especificação exige: broadcast
para múltiplos consumidores, `Emitter`, `MutinyEmitter`, `Message`, interceptors e
estratégias de falha.

## 2. As três formas de declarar um método

```java
@Outgoing("ticks")                        // produtor
public Multi<Long> aFewTicks() { ... }

@Incoming("ticks") @Outgoing("times")     // processador
public Multi<String> processor(Multi<Long> ticks) { ... }

@Incoming("times")                        // consumidor
public void consumer(String payload) { ... }
```

🧪 **No repositório**, o consumidor é sempre um método `Uni<Void>` que **representa** o
resultado, porque a pipeline precisa saber se houve sucesso ou falha:

```java
@Incoming("reservation-confirmed-in")
public Uni<Void> consume(String payload) {
    return Uni.createFrom()
            .item(() -> deserialize(payload))
            .flatMap(event -> inboxProcessor.process(
                    event.eventId(),
                    () -> consumer.handle(event)))
            .onFailure().invoke(f -> LOG.errorf("... %s", f.toString(), f));
}
```

Fonte: `billing-service/.../messaging/KafkaReservationConfirmedConsumer.java`

> 🔀 **Divergência deliberada:** `void consume(...)` funciona, mas **descarta** a falha.
> Com `Uni<Void>` o broker recebe o `nack` e a política de retry dispara. Void + log é a
> forma mais comum de "retry que nunca existe".

## 3. Acknowledgment: onde a mensagem é confirmada

⚠️ **Toda mensagem precisa ser confirmada. Ausência de ack é falha.**

Estratégias (verificadas em `smallrye-reactive-messaging-api` 4.37.0):

| Estratégia | Comportamento |
|---|---|
| `MANUAL` | você chama `message.ack()` / `message.nack(t)` |
| `PRE_PROCESSING` | ack **antes** de o método rodar |
| `POST_PROCESSING` | ack **depois** do método terminar (ou nack em exceção) |
| `NONE` | o ack é feito em outro lugar |

Defaults por assinatura:

| Assinatura | Default |
|---|---|
| `@Incoming("c") void m(I payload)` | `POST_PROCESSING` |
| `@Incoming("c") CompletionStage<?> m(I payload)` | `POST_PROCESSING` |
| `@Incoming("in") @Outgoing("out") Message<O> m(Message<I> msg)` | `MANUAL` |
| `@Incoming("in") @Outgoing("out") O m(I payload)` | `POST_PROCESSING` |

```java
@Incoming("channel-name")
@Acknowledgment(Acknowledgment.Strategy.MANUAL)
public CompletionStage<Void> consumer(Message<String> message) {
    return processMessage(message.getPayload())
            ? message.ack()
            : message.nack(new IllegalStateException("..."));
}
```

Os acks sobem na **direção contrária** ao fluxo das mensagens: só o consumidor final
confirma, e aí os processadores confirmam de trás para frente, até o produtor.

🔀 **No repositório** usamos o default `POST_PROCESSING` com `Uni<Void>`: falha na pipeline
⇒ `nack` ⇒ retry. Explícito e previsível.

## 4. Ponte entre código imperativo e reativo (cap. 9.3.4)

`@Channel` + `Emitter` (ou `MutinyEmitter`) é a saída para código que não é pipeline:

```java
@Inject @Channel("requests") Emitter<String> requestsEmitter;

requestsEmitter.send(body);                                 // não bloqueia
requestsEmitter.send(body).toCompletableFuture().join();    // bloqueia até o ack
```

E o inverso — consumir um canal como `Multi`/`Publisher` (útil para SSE):

```java
@Inject @Channel("ticks") Multi<Long> ticks;

@GET @Path("/consume") @Produces(MediaType.SERVER_SENT_EVENTS)
public Multi<Long> sseTicks() { return ticks; }
```

🧪 **No repositório**, `MutinyEmitter` é a forma usada no publicador de eventos, porque
precisamos mandar **chave + valor** e respeitar o backpressure:

```java
public Uni<Void> publish(OutboxEvent event) {
    return emitter.sendMessage(Message.of(Record.of(event.aggregateId(), event.payload())));
}
```

Fonte: `billing-service/.../messaging/InvoiceOpenedKafkaPublisher.java`

`event.aggregateId()` é o `invoiceId` — e isso **não** é detalhe: é o que garante ordenação
por agregado no Kafka. Ver [09](./09-padroes-de-resiliencia-em-messaging.md) §5.

## 5. Connectors (cap. 9.5)

Um connector é a SPI que liga um canal a um sistema externo. O Quarkus auto-detecta:

> Se o **único** connector no classpath é `smallrye-kafka`, o canal `invoices` vira tópico
> Kafka `invoices` sem configuração adicional.

No log do dev mode (saída **do livro**, com o pacote de exemplo dele):

```text
Configuring the channel 'invoices-adjust' to be managed by the connector 'smallrye-kafka'
Generating Jackson serializer for type org.acme.rental.billing.InvoiceAdjust
```

`org.acme.rental.billing.InvoiceAdjust` **não existe** neste repositório: o auto-detect do
capítulo fica como referência, e a configuração real do projeto é a explícita, logo abaixo.

🧪 **No repositório** a configuração é **explícita**, mesmo quando o auto-detect do livro funcionaria:

```properties
mp.messaging.incoming.reservation-confirmed-in.connector=smallrye-kafka
mp.messaging.incoming.reservation-confirmed-in.topic=reservation-confirmed
mp.messaging.incoming.reservation-confirmed-in.group.id=billing-service
mp.messaging.incoming.reservation-confirmed-in.value.deserializer=org.apache.kafka.common.serialization.StringDeserializer
mp.messaging.incoming.reservation-confirmed-in.auto.offset.reset=earliest
```

Motivo: com 3 canais Kafka explícitos, o log de startup diz exatamente qual canal foi
para onde. Auto-detect esconde isso.

## 6. Testes de messaging com Kafka (cap. 9.6.2)

O livro usa o **Kafka Companion**:

> "It is not intended to mock Kafka, but to the contrary, connect to a Kafka broker and
> provide high-level features."

Dependência:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-test-kafka-companion</artifactId>
    <scope>test</scope>
</dependency>
```

```java
@QuarkusTest
@QuarkusTestResource(KafkaCompanionResource.class)
class RentalResourceTest {
    @InjectKafkaCompanion KafkaCompanion kafkaCompanion;

    @Test
    void shouldSendAdjustment() {
        ConsumerTask<String, String> task = kafkaCompanion
                .consumeStrings().fromTopics("invoices-adjust", 1)
                .awaitNextRecord(Duration.ofSeconds(10));
        assertEquals(1, task.count());
    }
}
```

### 6.1 A configuração do projeto: um companion para a suíte inteira

🔀 **Divergência forte em relação ao livro.** O livro declara o resource por classe. Aqui
há **um** resource para a suíte toda:

```java
@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
class OutboxRelayKafkaIntegrationTest { ... }
```

Por quê (verificado no fonte do `quarkus-test-kafka-companion` 3.39.3):

```java
// io.quarkus.test.kafka.KafkaCompanionResource#start()  (fonte 3.39.3)
if (kafkaCompanion == null && kafka != null) {   // so cria o broker se ainda nao existe
    kafka.start();
    await().until(kafka::isRunning);
    kafkaCompanion = new KafkaCompanion(kafka.getBootstrapServers());
    return Collections.singletonMap("kafka.bootstrap.servers", kafka.getBootstrapServers());
}
return Collections.emptyMap();                   // reutiliza: nao devolve bootstrap
```

Se cada teste declarar o seu, cada um sobe **um** broker, e a aplicação só recebe o
`bootstrap.servers` de **um** deles. Tópicos criados no broker A são **invisíveis** para a
aplicação conectada no broker B. `restrictToAnnotatedClass = false` faz o resource ser
iniciado **uma vez** e reaproveitado.

### 6.2 Tópicos pré-criados

`BillingKafkaCompanionResource.start()` cria os tópicos antes de a app conectar, para não
depender de auto-criação do broker:

```java
private static final List<String> TOPICS = List.of(
        "vehicle-registered", "vehicle-registered-retry_50", ... , "invoice-opened");
```

`invoice-opened` é crítico: **ninguém publica nele** até o `OutboxRelay` rodar, então um
teste que precise ler o end offset antes de publicar não encontraria o tópico.

### 6.3 Tópicos de retry com nomes de perfil

A política de produção usa `retry_1000 / retry_5000 / retry_15000` (segundos). Em teste,
1s/5s/15s deixaria a suíte lenta demais, então `%test` sobrescreve:

```properties
mp.messaging.incoming.reservation-confirmed-in.delayed-retry-topic.topics=reservation-confirmed-retry_1000,...
%test.mp.messaging.incoming.reservation-confirmed-in.delayed-retry-topic.topics=reservation-confirmed-retry_50,reservation-confirmed-retry_100,reservation-confirmed-retry_200
```

É a mesma propriedade com override de perfil — a estratégia de retry testada é **a mesma
do produção**, só que acelerada.

### 6.4 O relay do outbox

O relay é `@Scheduled` **e** roda reativamente. Em teste, o scheduler é desligado para
não haver publicação em background:

```properties
%test.quarkus.scheduler.enabled=false
```

O teste então dispara o relay sob controle:

```java
asserter.assertThat(
        () -> onWorkerThread(() -> companion.offsets().get(partition, OffsetSpec.latest()).offset())
                .emitOn(eventLoop)
                .flatMap(endOffset -> relay.relay().replaceWith(endOffset))
                .flatMap(endOffset -> onWorkerThread(() -> awaitInvoiceOpenedAfter(partition, endOffset, invoiceId.get()))),
        record -> {
            assertEquals(invoiceId.get(), record.key(), "o outbox deve ser chaveado pelo invoice id");
            assertTrue(record.value().contains(reservationId), "o payload deve referenciar a reservation");
        });
```

Três decisões dentro desse teste:

1. **offset como critério de leitura** — `OffsetSpec.latest()` antes de publicar evita
   consumir registro de outro teste;
2. **filtro pela chave** — `records.select().where(r -> invoiceId.equals(r.key()))` garante
   que o registro lido é o nosso, não "o último do tópico";
3. **timeout explícito** — `awaitRecords(1, Duration.ofSeconds(10))`; sem isso, uma falha de
   publicação vira hang.

## 7. Connectors no projeto

| Conector | Serviço | Canais |
|---|---|---|
| `smallrye-kafka` | `inventory-service` (out) | `vehicle-registered-out` |
| `smallrye-kafka` | `billing-service` (in) | `vehicle-registered-in`, `reservation-confirmed-in`, `rental-completed-in` |
| `smallrye-kafka` | `billing-service` (out) | `invoice-opened-out` |
| `smallrye-reactive-messaging-in-memory` | `billing-service` (test) | canais de teste unitário |

RabbitMQ **não** foi adotado: o capítulo mostra os dois, o projeto escolheu Kafka. A razão
está registrada em [09](./09-padroes-de-resiliencia-em-messaging.md) — Kafka dá
**ordenação por chave** e **histórico** (retry topics, DLQ, consumer group), que é o que
o modelo de consistência do projeto precisa.

## 8. Checklist de estudo

- [ ] Sei por que um canal tem exatamente um produtor e um consumidor.
- [ ] Sei listar as 4 estratégias de `@Acknowledgment` e os defaults por assinatura.
- [ ] Sei explicar por que `void` em `@Incoming` pode "engolir" a falha.
- [ ] Sei explicar o que `restrictToAnnotatedClass = false` evita, quoting o fonte.
- [ ] Sei dizer por que `invoice-opened` precisa ser pré-criado.
- [ ] Sei explicar por que `%test` sobrescreve os topics de retry.
- [ ] Sei justificar Kafka em vez de RabbitMQ neste projeto.

**Veja também:** [09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md) ·
[07-programacao-reativa.md](./07-programacao-reativa.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md)

---

_Última atualização: 2026-09-26 (cap. 9; `Acknowledgment` e `KafkaCompanionResource`
conferidos nos fontes de `smallrye-reactive-messaging-api` 4.37.0 e
`quarkus-test-kafka-companion` 3.39.3)._
