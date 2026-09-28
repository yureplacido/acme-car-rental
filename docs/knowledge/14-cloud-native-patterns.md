# 14 — Cloud-native patterns

> Capítulo 10 do *Quarkus in Action* (p. 273–302 no impresso; PDF p. 299–329).
> Norma do projeto: [ddd-tdd-standards.md](../ddd-tdd-standards.md) §9 (regras de negócio em
> agregados, não em adapters) e AGENTS.md regra 16 (API verificada contra Quarkus 3.39.3).
> Última atualização: 2026-09-28 (itens health 1–5 e métricas do pipeline/relay — item 6 — concluídos).

---

## 1. Conceitos do capítulo

### 1.1 MicroProfile, SmallRye e Quarkus

- **O que é:** conjunto de especificações para aplicações cloud-native em Java; SmallRye é a
  implementação do MicroProfile que o Quarkus empacota.
- **Por que existe:** as mesmas capacidades (health, metrics, tracing, FT, service discovery)
  em qualquer runtime, sem amarrar a aplicação ao fornecedor.
- **Quando usar / quando não usar:** usar as **extensões nativas do Quarkus**, que já trazem a
  implementação SmallRye; não abstrair o que já é padrão.
- **Armadilha clássica:** criar abstração própria "para desacoplar" e acabar desacoplando de
  nada, com custo de manutenção.

### 1.2 Health: liveness, readiness e startup

- **O que é:** três grupos de checks. Liveness = o processo ainda está vivo. Readiness = pronto
  para receber tráfego. Startup = inicialização terminou.
- **Por que existe:** orquestradores usam liveness/readiness para matar e rotear; startup evita
  que readiness flague falso "não pronto" durante boot lento.
- **Quando usar / quando não usar:** separar os grupos desde o início; os checks nativos do
  Quarkus (banco, conexões) participam do readiness quando aplicável.
- **Armadilha clássica:** marcar tudo como liveness e matar o pod por dependência temporária.

### 1.3 Metrics: Micrometer, tipos e dimensionalidade

- **O que é:** `Counter` (monotônica), `Gauge` (valor corrente), `Timer`/`DistributionSummary`
  (durações/distribuições); registros têm nome + tags (dimensionalidade).
- **Por que existe:** Prometheus precisa de séries bem nomeadas com rótulos para agregar.
- **Quando usar / quando não usar:** counter para contagens que só crescem, gauge para estado
  corrente (ex.: backlog/thing em fila). **Nunca** decidir com `Counter` o que é `Gauge`.
- **Armadilha clássica:** usar gauge para coisa monotônica (cola de restart) ou jogar `1` no
  counter a cada scrape.

### 1.4 Prometheus e Grafana

- **O que é:** armazenamento de séries temporais (pull) + visualização.
- **Por que existe:** o Quarkus expõe o registry Micrometer em `/q/metrics` no formato Prometheus.
- **Quando usar / quando não usar:** precisa do artefato `quarkus-micrometer-registry-prometheus`
  — sem ele o endpoint `/q/metrics` não existe, mesmo com `quarkus-micrometer` presente.
- **Armadilha clássica:** habilitar `quarkus-micrometer` e procurar `/q/metrics`.

### 1.5 Kafka client metrics (lag)

- **O que é:** métricas do cliente Kafka (consumers/producers) registradas no Micrometer:
  `kafka.consumer.fetch.manager.records.lag.max`, bytes/hits, etc.
- **Por que existe:** lag alto é o primeiro sinal de consumer atrasado.
- **Quando usar / quando não usar:** habilitadas automaticamente quando Micrometer + client
  Kafka existem (`quarkus.micrometer.binder.kafka.enabled=true`; o default do binder Kafka vem
  do Quarkus — verificado no guia oficial "Micrometer").
- **Armadilha clássica:** ligar JMX do broker para ver lag, em vez de expor os client metrics.

### 1.6 Channel metrics (observabilidade por canal)

- **O que é:** `quarkus.messaging.message.{count,acks,failures,duration}`, tag `channel`.
- **Por que existe:** mostra produção/consumo e falhas por canal do Reactive Messaging.
- **Quando usar / quando não usar:** **não é default por backward compatibility** — habilita com
  `smallrye.messaging.observation.enabled=true` (verificado no guia oficial "Messaging").
  Observação não cobre canais com tipo de payload customizado tipo `IncomingKafkaRecord`.
- **Armadilha clássica:** assumir que a métrica por canal existe sem habilitar a observação.

### 1.7 Tracing, fault tolerance, service discovery 🔜

- **O que é:** OpenTelemetry (propagação de contexto via Kafka), SmallRye Fault Tolerance
  (timeout/retry/fallback), Stork (descoberta de serviço).
- **Quando usar / quando não usar:** ver [roadmap.md](../roadmap.md) cap. 10 — itens 7–10 ainda
  **não concluídos**.

---

## 2. O que o repositório fez

### 2.1 Mapa capítulo → código

| Conceito | Onde está no código | Teste que prova |
|---|---|---|
| Health liveness/readiness/startup | todos os 5 serviços (`quarkus-smallrye-health`) | `*HealthEndpointTest` (ex.: billing `HealthEndpointTest`) |
| Métricas HTTP e de runtime em `/q/metrics` | Inventory com `quarkus-micrometer-registry-prometheus` | `HealthEndpointTest.shouldExposeHttpAndJvmMetrics` (inventory) |
| Métrica de negócio (porta da aplicação) | `inventory/.../port/out/InventoryMetrics.java` + `adapter/out/observability/MicrometerInventoryMetrics.java` | `RegisterVehicleTest` (fake da porta), `BusinessMetricsIntegrationTest` |
| Métricas do relay da outbox | billing `port/out/OutboxMetrics.java` + `adapter/out/observability/MicrometerOutboxMetrics.java` | `PublishPendingOutboxEventsTest`, `MicrometerOutboxMetricsTest`, `OutboxMetricsIntegrationTest` |
| Client metrics Kafka (lag) | billing `smallrye.messaging.observation.enabled` + binder kafka | `OutboxMetricsIntegrationTest` (scrape `/q/metrics`) |
| Channel metrics `quarkus.messaging.message.*` | billing `application.properties` | `OutboxMetricsIntegrationTest` |
| Tracing ponta a ponta | 🔜 ainda não implementado | — |
| Fault tolerance (SmallRye FT) | 🔜 ainda não implementado | — |
| Service discovery (Stork) | 🔜 ainda não implementado | — |

> A tabela acima é o índice; cada conceito implementado tem trecho embutido abaixo.

### 2.1.1 Métrica de negócio do Inventory atrás de porta da aplicação

`inventory-service/src/main/java/org/acme/inventory/application/port/out/InventoryMetrics.java` —
o caso de uso informa "veículo registrado" sem conhecer Micrometer:

```java
package org.acme.inventory.application.port.out;

public interface InventoryMetrics {

    void vehicleRegistered();
}
```

`inventory-service/src/main/java/org/acme/inventory/adapter/out/observability/MicrometerInventoryMetrics.java` —
a implementação do Micrometer mora no adapter:

```java
@ApplicationScoped
public class MicrometerInventoryMetrics implements InventoryMetrics {

    private final Counter vehiclesRegistered;

    public MicrometerInventoryMetrics(MeterRegistry registry) {
        this.vehiclesRegistered = registry.counter("inventory.vehicles.registered");
    }

    @Override
    public void vehicleRegistered() {
        vehiclesRegistered.increment();
    }
}
```

**Por que importa:** regra 9 do AGENTS.md — métrica é **efeito colateral observável**, não parte
da regra de negócio. O aggregate/uso de caso emite a intenção; quem mede é escolha de
infraestrutura, trocável sem tocar no domínio.

### 2.1.2 Porta `OutboxMetrics` no billing

`billing-service/src/main/java/org/acme/billing/application/port/out/OutboxMetrics.java` —
simétrica à do Inventory, no contexto de cobrança. O uso de caso que relata o relay passa a
receber a porta no construtor:

```java
package org.acme.billing.application.port.out;

public interface OutboxMetrics {

    void eventRelayed();

    void relayFailed();
}
```

### 2.1.3 Wiring no relay: métrica no caminho de sucesso e de falha

`billing-service/src/main/java/org/acme/billing/application/usecase/PublishPendingOutboxEvents.java` —
o contador de sucesso é registrado **depois** do `markPublished`; a falha registra **no mesmo
decorator** que incrementa as tentativas, sem duplicar o fluxo:

```java
private Uni<Void> publishOne(OutboxEvent event) {
    return eventPublisher.publish(event)
            .flatMap(ignored -> outboxEventStore.markPublished(event, Instant.now()))
            .invoke(ignored -> outboxMetrics.eventRelayed())
            .onFailure()
            .call(ignored -> {
                outboxMetrics.relayFailed();
                return outboxEventStore.incrementAttempts(event);
            });
}
```

**Por que importa:** sucesso só conta quando o evento foi **marcado** como publicado (não apenas
enviado ao broker). A falha conta junto com o incremento de tentativas — uma única fonte de
verdade para retry x falha.

### 2.1.4 Adapter Micrometer com contadores e gauge de backlog

`billing-service/src/main/java/org/acme/billing/adapter/out/observability/MicrometerOutboxMetrics.java` —

```java
@ApplicationScoped
public class MicrometerOutboxMetrics implements OutboxMetrics {

    private final Counter relayed;
    private final Counter failures;
    private final Counter backlogRefreshErrors;
    private final AtomicLong pendingBacklog;
    private final OutboxEventStore outboxEventStore;

    public MicrometerOutboxMetrics(
            MeterRegistry registry,
            OutboxEventStore outboxEventStore) {
        this.relayed = Counter.builder("billing.outbox.published")
                .description("Outbox events successfully relayed to Kafka")
                .register(registry);
        this.failures = Counter.builder("billing.outbox.failures")
                .description("Outbox events that failed to be relayed")
                .register(registry);
        this.backlogRefreshErrors = Counter.builder("billing.outbox.backlog.refresh.errors")
                .description("Failed attempts to measure the outbox backlog")
                .register(registry);
        this.pendingBacklog = registry.gauge(
                "billing.outbox.pending",
                new AtomicLong(0),
                AtomicLong::get);
        this.outboxEventStore = outboxEventStore;
    }

    @Override
    public void eventRelayed() {
        relayed.increment();
    }

    @Override
    public void relayFailed() {
        failures.increment();
    }

    @Scheduled(
            every = "5s",
            concurrentExecution = Scheduled.ConcurrentExecution.SKIP)
    Uni<Void> refreshBacklog() {
        return outboxEventStore.countPending()
                .invoke(pendingBacklog::set)
                .onFailure()
                .invoke(error -> {
                    backlogRefreshErrors.increment();
                    LOG.warnf(error, "Could not refresh the outbox backlog gauge");
                })
                .replaceWithVoid();
    }
}
```

**Por que importa:** a **atualização** do gauge é agendada no adapter, não no domínio.
`countPending()` entrou como método na porta `OutboxEventStore` (o uso de caso não precisa dele;
quem quer medir backlog consulta a contagem). O agendamento replica o padrão do `OutboxRelay`
(`concurrentExecution = SKIP`): nunca tem duas consultas de backlog rodando ao mesmo tempo. O
método agendado **retorna `Uni<Void>`** (não `void`) para que o SKIP valha de fato — com `void`
o scheduler consideraria a invocação concluída no retorno imediato e permitiria consultas
sobrepostas (regra 14). Falha ao medir não é silenciosa: conta em
`billing.outbox.backlog.refresh.errors` e loga em WARN — a ausência de dados nunca vira um
gauge congelado "de mentira".

### 2.2 Configuração

`billing-service/src/main/resources/application.properties` — as duas linhas que ligam a
observação do pipeline:

```properties
# Cap.10 - observabilidade do pipeline Kafka.
quarkus.micrometer.binder.kafka.enabled=true
smallrye.messaging.observation.enabled=true
```

`pom.xml` do billing (e do inventory) — o endpoint `/q/metrics` depende deste artefato:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-micrometer-registry-prometheus</artifactId>
</dependency>
```

### 2.3 Dependências

| Serviço | `quarkus-micrometer` | `quarkus-micrometer-registry-prometheus` | `quarkus-smallrye-health` |
|---|---|---|---|
| billing | ✅ | ✅(cap.10 item 6) | ✅ |
| inventory | ✅ | ✅ | ✅ |
| rental / reservation / users | ✅ | — | ✅ |

---

## 3. Divergências do livro

| # | O que o livro faz | O que fazemos | Por quê | Onde está registrado |
|---|---|---|---|---|
| 1 | Métrica de negócio como classe Micrometer espalhada no código | porta da aplicação + adapter Micrometer | regra 9 do AGENTS.md: regra de negócio ≠ efeito observável; DDD | §2.1.1, 2.1.2 |
| 2 | Livro usa nomes/versões do Quarkus 3.15.1 | API conferida contra 3.39.3 | regra 16 do AGENTS.md | roadmap `[3.39.3]` |
| 3 | Health/metrics "de módulo" sem separar dependência | decisão explícita de itens 1–5: **sem** abstração própria para o que é padrão; abstração só onde é porta da aplicação | ddd-tdd-standards §9 | roadmap cap. 10 |
| 4 | Adapter de métrica só implementa a porta que usa | o gauge de backlog do billing **inverte a direção**: `MicrometerOutboxMetrics` chama `OutboxEventStore.countPending()` (porta que ele não implementa) | exceção billing-specific documentada em ddd-tdd-standards §5-Observability ("Recorded exception") e decisão 15 do architecture.md; só billing tem relay com backlog | §2.1.4, §3 |

---

## 4. Testes: camada por camada

| Camada | Teste criado | O que prova | Como roda |
|---|---|---|---|
| Application | `PublishPendingOutboxEventsTest` | contadores no caminho de sucesso e de falha; retry x falha | `./mvnw -pl billing-service test -Dtest=PublishPendingOutboxEventsTest` |
| Adapter | `MicrometerOutboxMetricsTest` | counter publicado/falhas, gauge de backlog via `refreshBacklog()` | `./mvnw -pl billing-service test -Dtest=MicrometerOutboxMetricsTest` |
| Integration | `OutboxMetricsIntegrationTest` | `/q/metrics` real: counter >= 1, gauge, client metric Kafka (lag), `quarkus_messaging_message_count_total{channel="invoice-opened-out"}` | `./mvnw -pl billing-service test -Dtest=OutboxMetricsIntegrationTest` |

Critérios do padrão (ver [04](./04-estrategia-de-testes-do-projeto.md)): fake da porta em teste
de application; `await()` não usado sob `@RunOnVertxContext` (o `OutboxMetricsIntegrationTest`
usa `UniAsserter`); RED visível no histórico (construtor novo de `PublishPendingOutboxEvents`).

O predicado do gauge (pendente = `published_at IS NULL`) é travado na persistência por
`BillingOutboxIntegrationTest.shouldCountOnlyEventsThatAreNotPublishedYet`, evitando que a
expectativa do gauge seja derivada da própria consulta sob observação.

---

## 5. Armadilhas

### 5.1 `/q/metrics` não existia com `quarkus-micrometer` sozinho

- **Sintoma:** `404` ao bater em `/q/metrics` no billing.
- **Causa:** o endpoint Prometheus vem do artefato `quarkus-micrometer-registry-prometheus`,
  que só o inventory tinha.
- **Correção:** adicionar o artefato no pom do billing.
- **Prevenção:** verificar a presença do `registry-prometheus` antes de afirmar "métricas
  expostas" num serviço.

### 5.2 Observação por canal não é default

- **Sintoma:** `quarkus_messaging_message_*` não aparece no scrape.
- **Causa:** `smallrye.messaging.observation.enabled=true` é necessário (backward compatibility).
- **Correção:** habilitar a propriedade (verificada no guia oficial, seção Observability).
- **Prevenção:** regra 16 — conferir a propriedade na doc, não no livro.

### 5.3 Asserção por prefixo pegava a linha do canal errado

- **Sintoma:** `quarkus_messaging_message_count_total{channel="dlq-test-in-dead-letter-queue"}`
  casava primeiro.
- **Causa:** filtrar por `startsWith` do nome da métrica ignora as **tags** da série.
- **Correção:** filtrar a linha pela substring com o canal (`{channel="invoice-opened-out"`).
- **Prevenção:** métricas são **dimensionadas por tags**; assertar série = nome + tag.

---

## 6. Conceitos que NÃO usamos (e por quê)

| Conceito do capítulo | Por que não usamos | Quando reavaliar |
|---|---|---|
| SmallRye Metrics (MP Metrics antigo) | `quarkus-smallrye-metrics` está deprecated; Micrometer é o caminho | nunca |
| Abstração própria de health | health é padrão do runtime; abstrair seria desacoplar de nada | nunca (regra) |
| Métrica de negócio fora de porta | conflita com regra 9 do AGENTS.md | nunca (regra) |

---

## 7. Checklist de estudo

- [x] 5 perguntas que eu deveria saber responder depois de ler o capítulo:
  1. Por que liveness, readiness e startup são grupos diferentes?
  2. Qual a diferença entre Counter e Gauge, e onde o backlog da outbox se encaixa?
  3. Quando `/q/metrics` existe e quando não?
  4. Como o client Kafka (lag) e as métricas por canal chegam ao scrape?
  5. Por que métrica de negócio fica atrás de uma porta da aplicação?
- [ ] 3 perguntas que não sei responder (viram tarefa):
  1. Como o contexto de tracing viaja pelo Kafka (headers) sem vazar para o payload?
  2. Stork resolve qual caso real aqui? (a resposta pode ser "nenhum, usa-se Kafka/DNS")
  3. Graceful shutdown: drenar outbox pendente como evidência executável?

---

## 8. Referências

| Tipo | Referência |
|---|---|
| Guia oficial | https://quarkus.io/guides/micrometer (Kafka section) |
| Guia oficial | https://quarkus.io/guides/messaging (Observability) |
| Guia oficial | https://quarkus.io/guides/smallrye-health |
| Página do repositório | [architecture.md](../architecture.md) (árvore billing/inventory) |
| Página do repositório | [roadmap.md](../roadmap.md) (Cap. 10) |
| Livro | *Quarkus in Action* cap. 10, p. 273–302; índice local `book-index/cap10.txt` |

---

## 9. Checklist de fecho (parcial — capítulo em progresso)

- [x] roadmap.md com status e evidência executável dos itens 1–6
- [ ] tracing, fault tolerance e service discovery (itens 7–10) ainda pendentes — 🔜
- [x] suíte do billing verde (61 testes)
- [x] guardians executados ao terminar o item 6 (dd-domain, architecture, tdd, quarkus-book)

---

## 10. Checklist de governança do capítulo

```text
[x] capítulo lido e anotado no momento da leitura
[x] conceitos nomeados como no livro (para busca futura)
[x] mapa capítulo → código → teste dos itens concluídos
[x] código referenciado por trecho embutido, não por link
[x] divergências com regra/ADR (itens 1–3 em §3)
[x] API verificada contra 3.39.3 (regra 16)
[x] armadilhas reais registradas (§5)
[x] conceitos recusados registrados com motivo (§6)
[x] índice + roadmap + README da docs atualizados (item 6 + doc 14 indexados)
[x] suíte verde
[x] guardians rodados (4) e fixes de sincronização aplicados após o fecho
```

---

**Veja também:** [README.md](./README.md) ·
[09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md) ·
[13-transactional-outbox.md](./13-transactional-outbox.md) ·
[12-modelo-para-novos-capitulos.md](./12-modelo-para-novos-capitulos.md)

---

_Última atualização: 2026-09-28 (cap. 10 itens health 1–5 e métricas do pipeline/relay da outbox — item 6 — concluídos; snippets alinhados ao código real e guardians executados; tracing/FT/service discovery/graceful shutdown pendentes)._