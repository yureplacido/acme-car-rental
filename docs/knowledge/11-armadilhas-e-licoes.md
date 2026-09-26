# 11 — Armadilhas e lições

> Erros **reais** deste repositório, como foram diagnosticados e como foram prevenidos.
> Toda entrada segue o formato: **sintoma → causa → correção → prevenção**.

Esta é a página mais específica do projeto e a que mais envelhece bem: ela registra o que
**realmente** quebra na prática, não o que deveria quebrar na teoria.

---

## 1. Dois `KafkaCompanionResource` = dois brokers

**Sintoma.** `OutboxRelayKafkaIntegrationTest` publicava no Kafka, mas o companion não
encontrava o registro. `awaitRecords` estourava o timeout mesmo com o relay funcionando.
Pior: a suíte subia **dois containers** Strimzi e ficava lenta.

**Causa.** Cada classe de teste declarava o seu próprio resource:

```java
@QuarkusTestResource(value = BillingFlowKafkaCompanionResource.class)   // broker A
@QuarkusTestResource(KafkaCompanionResource.class)                      // broker B
```

Lendo o fonte de `quarkus-test-kafka-companion` 3.39.3:

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

Cada instância tinha `kafkaCompanion == null`, então cada uma criou um broker. A aplicação
recebeu o `bootstrap.servers` de **um** deles. Topic criado no broker B é **invisível**
para a aplicação conectada no broker A. Resultado: a app "publicava" num broker que o teste
não olhava.

**Correção.** Um único resource, declarado com `restrictToAnnotatedClass = false`:

```java
@QuarkusTest
@QuarkusTestResource(value = BillingKafkaCompanionResource.class, restrictToAnnotatedClass = false)
```

**Prevenção.**

- [x] Todo teste Kafka do serviço declara o **mesmo** resource com `restrictToAnnotatedClass = false`.
- [x] Comment no resource explicando o porquê (o arquivo existe justamente para isso).
- [ ] CI que falhe se a suíte subir mais de um container de broker.

**lição.** Recurso de teste com `start()` que **aloca recurso externo** precisa ser
singleton. `restrictToAnnotatedClass = false` não é "configuração de teste": é o que
garante a unicidade.

---

## 2. `HR000068` — I/O bloqueante no event loop

**Sintoma.** `cannot be blocked` do Mutiny **ou** `HR000068` do Hibernate Reactive —
são bibliotecas diferentes e as mensagens não devem ser trocadas:

```text
# Mutiny (o caso deste projeto: leitura de offset + await na event loop)
java.lang.IllegalStateException: The current thread cannot be blocked: vert.x-eventloop-thread-0

# Hibernate Reactive (método dele chamado na thread errada)
HR000068: This method should exclusively be invoked from a Vert.x EventLoop thread;
          currently running on thread 'vert.x-eventloop-thread-0'
```

O `relay.relay()` nunca completava; o teste falhava só quando a suíte rodava com
`@RunOnVertxContext`.

**Causa.** Leitura de offset do Kafka (`companion.offsets().get(...)`) é **bloqueante** e
estava sendo executada **na event loop**, dentro de um `Uni`. A event loop não pode bloquear:
ela atende todas as requisições/testes daquele contexto.

**Correção.** Três passos explícitos:

```java
Executor eventLoop = command -> Vertx.currentContext().runOnContext(ignored -> command.run());

// 1. bloqueante → worker thread
Uni<Long> endOffset = Uni.createFrom()
        .item(() -> companion.offsets().get(partition, OffsetSpec.latest()).offset())
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());

// 2. volta para a event loop antes de encostar em código reativo da aplicação
endOffset.emitOn(eventLoop)
        .flatMap(offset -> relay.relay().replaceWith(offset))
// 3. nova leitura bloqueante → worker thread de novo
        .flatMap(offset -> onWorkerThread(() -> awaitInvoiceOpenedAfter(partition, offset, invoiceId.get())));
```

**Prevenção.**

- [x] Helper `onWorkerThread(Supplier<T>)` no teste, para não repetir a receita.
- [x] `docs/testing.md` proíbe `await().indefinitely()` sob `@RunOnVertxContext`.
- [ ] Lint/checklist de revisão: toda chamada a API bloqueante dentro de `Uni` passa por
  `runSubscriptionOn`.

**lição.** `Uni` **não** cria thread. Ele só troca de thread quem **pede**
(`runSubscriptionOn` assina em outra thread; `emitOn` continua em outra). I/O bloqueante de
biblioteca precisa de worker thread explícita mesmo dentro de um `Uni`.

---

## 3. Tópico que ninguém cria

**Sintoma.** `UNKNOWN_TOPIC_OR_PARTITION` ao tentar ler o end offset de `invoice-opened`.

**Causa.** `invoice-opened` é um tópico de **saída**: nada publica nele até o `OutboxRelay`
rodar. O teste que precisa ler o offset **antes** de publicar não encontra o tópico — a
auto-criação do broker não ajuda, porque ninguém mandou nada ainda.

**Correção.** Pré-criar no `start()` do resource:

```java
private static final List<String> TOPICS = List.of(..., "invoice-opened");

@Override
public Map<String, String> start() {
    Map<String, String> props = super.start();
    for (String topic : TOPICS) { createTopicIfMissing(topic); }
    return props;
}
```

**Prevenção.**

- [x] Lista de tópicos no resource é a **fonte única** (retry topics inclusive).
- [x] Teste que só cria tópico no `@BeforeEach` usa `createAndWait` com
      `catch (TopicExistsException)` — porque outro teste da classe pode ter criado antes.

**lição.** "Não depende de auto-criação de topic" é regra, não otimização. Tópicos de retry
e DLQ também precisam existir: o connector não os cria.

---

## 4. Ler "o último registro" em vez de "o meu registro"

**Sintoma.** O teste do relay pegava o registro de **outro** teste, ou falhava intermitente
conforme a ordem de execução.

**Causa.** `fromTopics("invoice-opened", 1)` lê a partir do começo do tópico. Tópico é
**estado global compartilhado** por todos os testes da suíte.

**Correção.** Três filtros empilhados:

```java
TopicPartition partition = KafkaCompanion.tp(TOPIC, 0);
long from = companion.offsets().get(partition, OffsetSpec.latest()).offset();   // antes de publicar

record → assertEquals(invoiceId.get(), record.key());                          // chave
record → assertTrue(record.value().contains(reservationId));                    // payload
```

Além disso, cada teste usa `UUID.randomUUID()` nos identificadores de negócio
(`"outbox-kafka-" + UUID.randomUUID()`, `"reservation-flow-" + UUID.randomUUID()`).

**Prevenção.**

- [x] Todo teste de integration gera ids únicos por execução.
- [x] Leitura por **offset capturado antes da ação**, nunca "do começo" nem "o último".
- [x] Asserção na **chave** do registro, não só no payload.

**lição.** Integração com broker é teste de sistema. Cada asserção precisa de um
**identificador de correlação** que só o teste corrente conhece.

---

## 5. `@Scheduled` publicando em background durante o teste

**Sintoma.** O `OutboxRelay` às vezes publicava antes do teste pedir, e o registro
desaparecia do offset esperado.

**Causa.** O relay é `@Scheduled(every = "5s")`. Com a suíte longa, o scheduler disparava
no meio do teste, publicando o outbox **sem** o teste controlar.

**Correção.**

```properties
# billing-service/src/test/resources/application.properties
# em src/test/resources não se usa o prefixo %test — o profile já é 'test'
quarkus.scheduler.enabled=false
```

O teste então dispara `relay.relay()` sob controle.

**Prevenção.**

- [x] Scheduler desligado em `%test` no serviço que usa relay.
- [x] `ConcurrentExecution.SKIP` no `@Scheduled` evita sobreposição em produção.

**lição.** Background job é **evento não determinístico** no teste. Ou você desliga o
scheduler, ou você faz o teste sincronizar com ele. Desligar é mais simples e mais
determinístico.

---

## 6. `TransactionalUniAsserter` mascarando o efeito do consumer

**Sintoma.** `BillingFlowKafkaIntegrationTest` lia o status da invoice como `DRAFT` depois
do `RentalCompleted` — mesmo com o consumer tendo commitado `OPEN`. O teste "passava" com
o valor errado (a asserção comparava com o que já sabia).

**Causa.** `TransactionalUniAsserter` abre uma transação que **envolve as asserções**. O
Hibernate Reactive mantém **cache de primeira nível** por sessão: a leitura seguinte, na
mesma sessão, enxerga o estado do DRAFT que foi carregado antes, não o que foi commitado
por outra transação.

**Correção.** Usar `UniAsserter` (sem transação envolvente), para que cada leitura veja o
efeito commitado:

```java
@Test @RunOnVertxContext
void shouldCreateDraftInvoiceThenOpenItOnRentalCompletion(UniAsserter asserter) { ... }
```

**Prevenção.**

- [x] Regra registrada em [roadmap.md](../roadmap.md) e aqui.
- [x] Em teste de consumer que **escreve** no banco, preferir `UniAsserter`.

**lição.** Cache de primeira camada não é detalhe de implementação: é **visibilidade**.
Teste de pipeline distribuído precisa ler o que foi **commitado**, não o que está no cache
da sessão do teste.

---

## 7. Retries de 1s/5s/15s em teste

**Sintoma.** `DelayedRetryKafkaIntegrationTest` consumia ~21s só de esperas de retry.

**Causa.** A política de produção usa `retry_1000 / retry_5000 / retry_15000` (1s, 5s, 15s).

**Correção.** Tópicos de retry acelerados no profile de teste, nos **canais sintéticos** que os
testes de resiliência realmente usam (`retry-test-in`, `retry-exhaustion-in`, `dlq-test-in`,
`transactional-inbox-retry-test-in`):

```properties
# billing-service/src/test/resources/application.properties
mp.messaging.incoming.retry-exhaustion-in.delayed-retry-topic.topics=retry-exhaustion-retry_50,retry-exhaustion-retry_100,retry-exhaustion-retry_200
```

Os canais reais (`vehicle-registered-in`, `reservation-confirmed-in`,
`rental-completed-in`) **também** têm override em `%test` (50/100/200ms), mas nenhum teste
de resiliência os consome hoje — ver a dívida registrada em
[09-padroes-de-resiliencia-em-messaging.md §3.3](./09-padroes-de-resiliencia-em-messaging.md).

**Prevenção.**

- [x] A lista de tópicos de `%test` está no resource único, então é uma fonte só.
- [x] O **nome** do tópico carrega o delay (`_50` = 50ms), então dá para ler no log.

**lição.** Teste de resiliência deve exercitar a **mesma configuração estrutural** da
produção, mudando só a escala de tempo. Se você trocar a estratégia em teste, o teste não
prova nada sobre produção.

---

## 8. Kafka 3.9.0 no compose: broker não sobe

**Sintoma.** O serviço `kafka` do `others/docker-compose.yml` falhava no healthcheck com
erro de validação de listener.

**Causa.** **KAFKA-18281**: com KRaft `3.9.0`, o broker validava listeners não-advertised
(ex.: `CONTROLLER`) contra `advertised.listeners`, e o `0.0.0.0` causava falha de
inicialização.

**Correção.** Imagem **`apache/kafka:3.9.1`**, não `3.9.0`.

**Prevenção.**

- [x] Registrado em [roadmap.md](../roadmap.md) com o número do bug.
- [x] Tópicos provisionados pelo serviço `kafka-init`, não à mão.

**lição.** Versão de broker é decisão de arquitetura. Fixe a versão e **cite o bug** que
motiva o pin.

---

## 9. Colisão de porta entre dev mode e teste

**Sintoma.** `quarkus dev` rodando enquanto `./mvnw test` executava: a segunda instância
não subia, ou o teste falhava ao falar com a porta errada.

**Correção.**

```properties
# .mvn/maven.config — vale para TODOS os módulos do agregador
-Dquarkus.http.test-port=0
```

```properties
# application.properties — só billing-service e reservation-service declaram isto
%test.quarkus.http.test-port=0
```

Porta `0` = qualquer porta livre. Isola o teste do dev **e** de execuções simultâneas.
O `.mvn/maven.config` é o que garante isso mesmo em serviço que não declara o perfil;
a regra está registrada em [architecture.md](../architecture.md).

**lição.** O capítulo 5 sugere `quarkus.http.test-port=<porta fixa>`. Isso resolve o
conflito com o dev, mas **não** resolve dois builds de teste ao mesmo tempo. `0` resolve os
dois.

---

## 10. `quarkus-junit5-mockito` não existe mais

**Sintoma.** Copiar o `pom.xml` do capítulo 5 falha em 3.39.3 (ou resolve por relocation,
sem erro visível).

**Causa.** A partir do **3.31**, `quarkus-junit5-mockito` é um stub de relocation:

```xml
<distributionManagement>
    <relocation>
        <artifactId>quarkus-junit-mockito</artifactId>
        <message>Refer to the Migration Guide 3.31</message>
    </relocation>
</distributionManagement>
```

**Correção.** `io.quarkus:quarkus-junit-mockito` (test scope). O próprio
`reservation-service/pom.xml` documenta a troca.

**lição.** Código de livro envelhece. Regra 16 do [AGENTS.md](../../AGENTS.md) existe para
isso: **toda API sensível à versão é conferida contra a versão fixada antes de ser usada**.

---

## 11. `TopicExistsException` ao criar tópico no `@BeforeEach`

**Sintoma.** `TopicExistsException` em `DlqKafkaIntegrationTest` /
`DelayedRetryKafkaIntegrationTest` quando os dois testes da classe rodavam em sequência.

**Causa.** O broker é compartilhado na suíte; o segundo teste da classe tentava recriar
tópico já criado pelo primeiro.

**Correção.**

```java
private void createTopicIfMissing(String topic) {
    try {
        companion.topics().createAndWait(topic, 1);
    } catch (TopicExistsException ignored) {
        // Topic already exists from another test in this test class.
    }
}
```

**lição.** `createAndWait` **não** é idempotente. Trate o estado global como tal.

---

## 12. Mutação de tópico dentro do teste (removida)

**Sintoma.** Uma versão do teste do relay **recriava/limpiava** o tópico `invoice-opened`
para garantir isolamento.

**Problema.** Apagar um tópico enquanto a aplicação está conectada é uma operação
**destrutiva** que pode afetar outros testes e, pior, **não é representativa** de
produção — em produção ninguém apaga tópico.

**Correção.** Isolamento por **offset + chave**, não por mutação de tópico
(`f642afa test(billing): avoid Kafka topic mutation in relay test`).

**lição.** "Deixar o teste limpo" às vezes significa **distorcer a realidade**. Prefira
correlação (id único, offset, chave) a limpeza destrutiva.

---

## 13. Receita: como debugar um teste de integration que falha

```text
1. A falha é determinística?
   ├─ sim → leia a exceção. Quase sempre é config ou ordem.
   └─ não → é estado compartilhado. Vá para 4.

2. O recurso externo (broker/banco) é o MESMO que a aplicação usa?
   ├─ não → problema de resource (armadilha 1)
   └─ sim → vá para 3

3. A falha é "não chegou nada" ou "chegou errado"?
   ├─ não chegou  → tópico existe? consumer conectado? offset?
   ├─ chegou errado → está lendo o registro de outro teste (armadilha 4)?
   └─ chegou, mas com valor velho → cache de primeira fase (armadilha 6)

4. Print do estado: topicos, offsets, estado do consumer
5. Isolamento: rode o teste sozinho
   ├─ passa sozinho, falha na suíte → estado compartilhado
   └─ falha sozinho → problema real no código ou config
```

**Regra:** um teste de integration que passa sozinho e falha na suíte **nunca** é "flaky".
É isolamento. Trate como bug.

---

## 14. Índice de armadilhas por sintoma

| Sintoma | Causa provável | Seção |
|---|---|---|
| I/O bloqueante na event loop (`cannot be blocked` / `HR000068`) | threads e event loop | §2 |
| `UNKNOWN_TOPIC_OR_PARTITION` | tópico não criado / auto-criação | §3 |
| `awaitRecords` timeout | companion em outro broker | §1 |
| Registro do teste não encontrado | estado compartilhado no tópico | §4 |
| `TopicExistsException` | criação não idempotente | §11 |
| Valor antigo lido após commit | cache de primeira fase | §6 |
| Publicação duplicada em teste | `@Scheduled` ativo | §5 |
| Teste lento com retry | delays de produção em `%test` | §7 |
| Dependência não sobe | versão do broker | §8 |
| Porta ocupada | `test-port` fixo | §9 |
| Dependência Maven não resolve | artifactId relocado | §10 |

**Veja também:** [04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) ·
[08-messaging-reativo.md](./08-messaging-reativo.md) ·
[07-programacao-reativa.md](./07-programacao-reativa.md)

---

_Última atualização: 2026-09-26 (12 armadilhas + receita e índice, extraídas dos commits de correção do
`billing-service` e do `roadmap`)._
