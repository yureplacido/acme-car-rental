# 07 — Programação reativa

> Capítulo 8 do *Quarkus in Action*.
> Norma do projeto: [ddd-tdd-standards.md](../ddd-tdd-standards.md) §8.

## 1. O problema que reatividade resolve (cap. 8.1)

No modelo imperativo, uma thread atende a requisição **inteira**. Se a requisição faz I/O
(banco, outro serviço, broker), a thread fica **ociosa** esperando:

```text
Thread 1:  [request] ----Schedule I/O----[idle]----I/O done----[schedule I/O 2]...
Thread 2:  [request] --------Schedule I/O 2----[idle]...
```

No modelo reativo, poucas threads de event loop **intercalam** o I/O de milhares de
requisiestas. A thread é liberada durante a espera.

**Mas**: isso só ajuda se a thread **realmente** for liberada. Se você bloqueia a event
loop, o ganho some e o dano é pior (latência de todas as requisiestas).

## 2. O modelo de execução do Quarkus

```text
requisição REST returning Uni      → event loop
requisição REST returning objeto   → worker thread (bloqueante por default)
@Blocking                          → força worker thread
@NonBlocking                       → força event loop
runSubscriptionOn(workerExecutor) → muda a thread da assinatura
@WithTransaction + Uni             → transação reativa, commit no fim da pipeline
```

🧪 **No repositório, isso é testado, não asumido.** Existe um endpoint de diagnóstico
que devolve o nome da thread:

```java
@Path("/reactive/execution")
public class ReactiveExecutionResource {
    @GET @Path("/event-loop")
    public Uni<String> eventLoop() { return Uni.createFrom().item(Thread.currentThread().getName()); }

    @GET @Path("/worker")
    @Blocking
    public Uni<String> worker() { return Uni.createFrom().item(Thread.currentThread().getName()); }
}
```

E o teste prova as duas afirmações:

```java
@Test void shouldExecuteReactiveEndpointOnEventLoop() {
    given().when().get("/reactive/execution/event-loop").then()
        .statusCode(200).body(containsString("vert.x-eventloop-thread"));
}
@Test void shouldExecuteBlockingEndpointOnWorkerPool() {
    given().when().get("/reactive/execution/worker").then()
        .statusCode(200).body(containsString("executor-thread"));
}
```

> `ReactiveExecutionResource` é **diagnóstico**, deliberadamente fora da API de negócio
> do Inventory. Está comentado no código como tal.

## 3. Mutiny: `Uni` e `Multi` (cap. 8.2)

| Tipo | Semântica | Quando |
|---|---|---|
| `Uni<T>` | **zero ou um** item | I/O pontual: salvar, buscar, publicar |
| `Multi<T>` | **stream** de 0..N | ingestão em lote, relay de outbox, ticks |

Operadores que o projeto usa e o que cada um compra:

| Operador | Compra | Teste correspondente |
|---|---|---|
| `.onItem().invoke(...)` | efeito colateral sem trocar tipo | — |
| `.onItem().call(...)` | encadear outro `Uni` | — |
| `.flatMap(...)` | encadear e achatar | — |
| `.replaceWith(x)` / `.replaceWithVoid()` | trocar de valor/tipo, ignorando o anterior | — |
| `.emitOn(executor)` | trocar de thread (com cuidado!) | `OutboxRelayKafkaIntegrationTest` |
| `.runSubscriptionOn(executor)` | assinar em outra thread | `OutboxRelayKafkaIntegrationTest` |
| `.onFailure().retry().withBackOff(d, max).withJitter(j).atMost(n)` | retry com atraso | `BillingFlowKafkaIntegrationTest` (em `RegisterVehicleRetryTest` o retry é só `.onFailure(IOException.class).retry().atMost(2)`, sem backoff) |
| `.ifNoItem().after(d).fail()` | timeout | `RegisterVehicleTimeoutTest` |
| `.onFailure().recoverWithNull()` | degradar sem falhar | `TransactionalInboxProcessorIntegrationTest` |

## 4. Concorrência é diferente de backpressure

Confundir os dois é o erro mais comum:

- **backpressure** = o consumidor diz quanto quer receber. `request(n)` no `Multi`.
- **limite de concorrência** = quantas subscriptions internas ficam ativas ao mesmo tempo.

🧪 **No repositório**, `BulkRegisterVehicles` usa `merge(maxConcurrency)` com
`inventory.bulk.max-concurrency=4`, e o teste prova que o upstream **não** emite sem demanda
do downstream:

```java
AssertSubscriber<Vehicle> subscriber = useCase.handle(commands)
        .subscribe().withSubscriber(AssertSubscriber.create(0));
subscriber.awaitSubscription();
assertEquals(2L, upstreamRequests.get());   // prefetch limitado pela concorrência
subscriber.assertHasNotReceivedAnyItem();   // nada entregue sem demanda
subscriber.request(1);
subscriber.awaitItems(1);
```

Esse teste é o **contrato de backpressure** do caso de uso. Sem ele, aumentar
`max-concurrency` para 1000 derrubaria o banco sem nenhum teste falhar.

## 5. Cancelamento é comportamento observável (cap. 8.4)

`Multi` cancelado deve **parar de produzir**. O projeto prova isso com
`BulkRegisterVehiclesCancellationTest`, e prova que timeout **cancela** a operação com
`RegisterVehicleTimeoutTest`:

```java
subscriber.awaitFailure().assertFailedWith(TimeoutException.class);
assertEquals(1, repository.started.get());
assertEquals(1, repository.cancelled.get());   // o cancelamento chegou no adapter
```

O segundo `assert` é o que importa: prova que o sinal de cancelamento **atravessou** todas
as camadas e chegou no adapter. Sem ele, o teste só mostraria que o `Uni` falhou.

## 6. `await()` sob event loop = `HR000068`

⚠️ **A armadilha mais cara de todo o projeto.** Em `OutboxRelayKafkaIntegrationTest` a
primeira versão lia o offset do Kafka direto no event loop e recebia:

```text
HR000068: Thread [...] interrupted while waiting on a non-blocking Mutiny method
```

Diagnóstico e correção:

```java
// 1. a leitura de offset do Kafka é BLOQUEANTE → assina em worker thread
Uni<Long> endOffset = Uni.createFrom().item(() -> companion.offsets().get(partition, OffsetSpec.latest()).offset())
        .runSubscriptionOn(Infrastructure.getDefaultExecutor());

// 2. volta para o event loop antes de tocar em código reativo da aplicação
endOffset.emitOn(eventLoop)
        .flatMap(offset -> relay.relay().replaceWith(offset))   // relay roda no event loop
        .flatMap(offset -> onWorkerThread(() -> awaitRecords(...)));
```

Três lições:

1. **`Uni` não cria thread.** Só muda de thread quem explicitamente pede
   (`runSubscriptionOn` / `emitOn`).
2. **`emitOn` é uma fronteira**: depois dela, o restante da pipeline roda na thread nova.
3. **I/O de biblioteca bloqueante** (`KafkaCompanion.offsets()`, JDBC síncrono) precisa de
   worker thread **explícita**, mesmo que esteja dentro de um `Uni`.

## 7. Domínio continua síncrono

Regra inegociável ([ddd-tdd-standards.md](../ddd-tdd-standards.md) §8):

> "The domain remains synchronous and framework-free even when adapters/application
> orchestration are reactive."

```text
Domain            → síncrono, sem Uni
Application       → Uni quando I/O assíncrono faz parte do caso de uso
Adapter           → reativo, cuida de event loop, timeout, retry, cancelamento
```

Teste de domínio com Mutiny é **erro de camada**, não conveniência.

## 8. Virtual threads / Project Loom (cap. 8.5)

O livro trata Loom como "imperativo com os benefícios do reativo", e lista 4 problemas
honestamente:

| Problema | Consequência |
|---|---|
| **pinning** de carrier thread (`synchronized`, JNI) | o carrier fica bloqueado também |
| **CPU-bound** em thread virtual | pior que platform thread (não há ganho, há overhead) |
| **scaling** de carriers | CPU e memória, até OOM |
| **`ThreadLocal` com objetos grandes** | cópia por thread virtual; `scoped values` ainda em preview |

🔀 **Decisão do projeto:** Loom **não** é o caminho adotado. O eixo é reatividade com
isolamento explícito de thread, porque é o que os casos de uso (I/O de banco, broker,
serviços) pedem. Loom entra no [roadmap](../roadmap.md) só se um caso for predominantemente
imperativo e CPU-bound — e mesmo assim, CPU-bound é o **pior** caso para Loom.

## 9. Checklist de estudo

- [ ] Sei explicar por que o retorno `Uni` de um método JAX-RS muda a thread de execução.
- [ ] Sei dizer o que `emitOn` e `runSubscriptionOn` fazem de diferente.
- [ ] Sei explicar por que concurrency limit ≠ backpressure.
- [ ] Sei reproduzir `HR000068` e dizer em qual thread cada trecho estava.
- [ ] Sei explicar por que `await().indefinitely()` é proibido no event loop.
- [ ] Sei listar os 4 problemas de Loom e dizer por que o projeto não o adota.

**Veja também:** [04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) §5 ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md) ·
[06-persistencia-transacoes-e-nosql.md](./06-persistencia-transacoes-e-nosql.md)

---

_Última atualização: 2026-09-26 (cap. 8)._
