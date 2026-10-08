# 14 — Cloud-native patterns

> Capítulo 10 do *Quarkus in Action* (p. 273–302 no impresso; PDF p. 299–329).
> Norma do projeto: [ddd-tdd-standards.md](../ddd-tdd-standards.md) §9 (regras de negócio em
> agregados, não em adapters) e AGENTS.md regra 16 (API verificada contra Quarkus 3.39.3).
> Última atualização: 2026-10-08 (itens health 1–5, métricas do pipeline/relay — item 6 —,
> tracing ponta a ponta — item 7, fault tolerance nas chamadas externas — item 8 —, service
> discovery (Stork/Consul) — item 9 — e configuração cloud-native — item 10 — concluídos;
> graceful shutdown ainda pendente).

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

### 1.7 Tracing, fault tolerance, service discovery

- **O que é:** OpenTelemetry (propagação de contexto via Kafka), SmallRye Fault Tolerance
  (timeout/retry/fallback), Stork (descoberta de serviço).
- **Tracing (implementado, item 7):** com `quarkus-opentelemetry` presente, a propagação de
  contexto pelo Kafka é **automática** (guia oficial "Messaging", seção OpenTelemetry Tracing):
  mensagens de saída propagam o span corrente no header `traceparent`; mensagens de entrada
  herdam o span do record como pai. Nenhum código de domínio ou adapter muda — o contexto
  viaja no header, nunca no payload. Em dev, o Dev Service LGTM (Grafana+Tempo) sobe sozinho.
- **Fault tolerance (implementado, item 8):** a política mora nas annotations dos
  **adapters de saída** (`adapter/out`), nunca na porta nem no caso de uso — ela descreve a
  fronteira técnica. O retry só entra onde a operação é idempotente, e o fallback **sinaliza**
  em vez de devolver valor falso. Detalhes e alternativas em
  [adr/009](../adr/009-fault-tolerance-chamadas-externas.md); resumo abaixo.
- **Service discovery (implementado, item 9):** Stork + Consul para as saídas REST Client
  (`users→reservation` como `stork://reservations`, `reservation→rental` como `stork://rentals`).
  O lado da **publicação** é um adapter próprio de registro
  (`adapter/out/registration/ConsulServiceRegistration`, em `reservation-service` e
  `rental-service`): publica `reservations`/`rentals` no boot com health check HTTP de URL
  absoluta e deregistra no shutdown — o auto-registro do Stork tem dois defectos na 3.39.3
  (ver "Armadilhas medidas"), então **o registro é assumido pelo serviço e só a descoberta
  é Stork**. Decisão em [adr/010](../adr/010-service-discovery.md). Configuração essencial em
  [services.md](../services.md) (notas dos respectivos serviços) e detalhes em "Armadilhas medidas
  neste projeto".
- **Quando usar / quando não usar:** o Stork integra **REST Client e gRPC** — o cliente GraphQL
  (SmallRye GraphQL Client) **não** participa, e a saída `reservation→inventory` segue com URL
  externalizada (`INVENTORY_SERVICE_URL`), **divergência documentada**. O livro (*Quarkus in
  Action* 10.6, p. 301–302) não implementa o Stork: para produção aponta o **service discovery
  da plataforma (Kubernetes/OpenShift)**.
- **Configuração cloud-native (implementado, item 10):** a **mesma imagem** sobe em qualquer
  ambiente; o que muda é `QUARKUS_PROFILE` no launch. Cada serviço publica
  `acme/<artifactId>:${project.version}` via Maven profile `docker`
  (`quarkus-container-image-docker`), o compose **consome** (`image:` + `pull_policy: never`,
  `ACME_IMAGE_TAG` no `others/.env`) e o runtime decide o catálogo: `%docker` → **Consul**,
  `%kubernetes` → **Stork provider `kubernetes`**, `%prod` → jar no host (localhost). Em
  `%kubernetes` o registro Consul é desligado nos publishers reservation e rental
  (`%kubernetes.acme.consul.registration.enabled=false`): em K8s quem publica é a plataforma
  (Service), não o app.
  Testes de discovery selecionam o backend por tag (`@Tag("consul")`/`@Tag("kubernetes")`, no
  surefire via `acme.test.discovery.excludedGroups`, profile `-P kubernetes` troca o excluído).
  Manifests Kubernetes versionados em `others/k8s/` (`quarkus-kubernetes`). Decisão em
  [adr/011](../adr/011-imagens-e-perfis-cloud-native.md); async em "Armadilhas medidas".
  Pendente no cap. 10: graceful shutdown.
- **Armadilhas medidas neste projeto:**
  - **`@Timeout` do SmallRye FT em método que devolve `Uni` não cancela a subscription a
    montante.** Emite `TimeoutException` para o chamador e deixa a chamada em voo. Caracterizado
    em teste com `Uni.onCancellation()` (`shouldKeepTheWriteCallInFlightWhenTheFaultToleranceDeadlineFires`,
    `shouldFailWithInventoryUnavailableWhenTheReadKeepsTimingOut`). Para a escrita isso segura
    conexão HTTP, então o prazo do transporte (`quarkus.rest-client."<cliente>".read-timeout`) é
    configurado **abaixo** do deadline de FT, e um teste de guarda compara os dois.
  - **Unidade entre annotation e config.** `timeout.value` sem `timeout.unit` **herda** a unidade
    da annotation (`Timeout.unit`, padrão `MILLIS`): com a annotation em segundos, `300` vale
    300 s (medido, 300002 ms) — a mesma linha de config muda de significado conforme a annotation.
    Sempre escrever `timeout.unit` e medir o tempo decorrido no teste, com faixa bilateral e
    `@Timeout` de classe.
  - **`@InjectMock` de um REST client exige o qualifier `@RestClient` no campo**
    (`@InjectMock @RestClient RentalClient client`); sem ele a resolução do bean falha, porque
    o bean registrado só tem o qualifier `@RestClient`.
  - **Stork exige o provider no classpath no build-time.** Configurar
    `quarkus.stork.<svc>.service-discovery.type=consul` sem o artefato
    `stork-service-discovery-consul` quebra o boot com "config property ... is required" —
    um dos RED do item 9. O artefato está no BOM do Quarkus (versão SmallRye Stork gerenciada).
  - **Consul no classpath exige esse Jackson.** `SmallRyeStorkProcessor.
    checkThatJacksonExtensionIsUsedWhenConsulIsOnTheClasspath` derruba a augmentação quando há
    provider Consul do Stork e **não** há `quarkus-jackson`. Nos consumidores cai de passagem pelo
    REST Client (`quarkus-rest-client-jackson` → `quarkus-jackson`); um módulo Stork-Consul sem
    REST Client precisa acrescentar `quarkus-jackson` explicitamente.
  - **As chaves do provider Consul entram direto no prefixo do serviço**, sem sub-bloco
    `params`: `quarkus.stork.<svc>.service-discovery.type=consul` + `.consul-host`/`.consul-port`
    (+ `.use-health-checks=true` default, `.refresh-period` em segundos). O provedor filtra o
    catálogo por `?passing=true` quando o use-health-checks está ligado, então o registro do
    serviço de destino **precisa ter check HTTP que o Consul consiga executar** — foi o defeito
    que a primeira implementação tinha (abaixo). **Consul agent `-dev` não conserta checks de
    instâncias que morrem sem deregistrar**; aqui os serviços deregistram no shutdown e, nos
    testes, o próximo registro do mesmo ID sobrescreve.
  - **O auto-registro do Stork em 3.39.3 é defeituoso; o registro é adapter próprio.** (1) O
    recorder `StorkRegistrarConfigRecorder`/`StorkConfigUtil.addRegistrarTypeIfAbsent` injeta
    `health-check-url` **relativa** (`q/health/live`, derivada dos defaults do SmallRye Health),
    que o Consul marca como **critical desde o boot** e que o `DeregisterCriticalServiceAfter`
    default (1m) remove do catálogo — discovery com `passing=true` volta 0 em `%prod`/`%docker`
    e o usuário não tem override possível (o `put` sobrescreve). (2) O deregister roda como
    última shutdown task, **depois do CDI fechado**, e explode com "No CDI container is
    available" quando o classpath tem narayana-jta (caso que o rental tinha). Por isso
    `ConsulServiceRegistration` (adapter em cada serviço que se publica) registra via API do
    Consul com URL absoluta `http://<address>:<port>/q/health/live` e deregistra no
    `@PreDestroy` (garantido pelo container). Os testes negam os dois sintomas: check
    **passing** (o Consul alcança o `/q/health/live` do JVM via host-gateway) e saída limpa do
    catálogo após o deregister.
  - **REST Client com `@AccessToken` aborta 401 sem request autenticado.** O
    `AccessTokenRequestReactiveFilter` sintetiza 401 quando o token propagado é nulo
    ("Injected access token is null, aborting the request with HTTP 401 error"), e o
    `OidcTokenCredentialProducer` é request-scoped (roda no event-loop, sem request context no
    teste). Por isso a prova do BFF fica no nível do **Stork**; o caminho completo
    REST Client→Stork→Consul é provado onde não há token (`RentalServiceDiscoveryTest`).
  - **`@AccessToken` só faz sentido quando a saída ecoa o token do chamador.** O BFF
    (users→reservation) propaga o token OIDC do usuário da sessão. Já o salto **interno**
    reservation→rental **não** propaga (cliente sem `@AccessToken`): reencaminhar token do
    próprio serviço (de serviço a serviço) raramente é o pretendido.
  - **Stork-k8s (3.39.3) exige `k8s-namespace` e `targetRef` e endpoint sem slice.** O provider
    `kubernetes` (artefato `stork-service-discovery-kubernetes`, no BOM) NPE em
    `gatherBackendPods` sem `k8s-namespace`; o `EndpointAddress` precisa de `targetRef`
    preenchido (senão classifica a instância como inválida); e com `quarkus.stork.<svc>….
    service-discovery.use-endpoint-slices` no default (auto-detect) o provider usa
    **EndpointSlices** quando o API server as oferece (falha → fallback Endpoints) — no mock dos
    testes pinçamos `use-endpoint-slices=false` para o caminho de Endpoints ser determinístico
    (`client.resource(ep).create()`). Medido no item 10; o runtime **não** fixa essa chave
    (auto-detect em cluster real), e a config `%kubernetes` (type + `k8s-namespace`) vive no
    `application.properties` de users e reservation.
  - **CRUD mock do fabric8 não emite o POST de Endpoints de qualquer forma.** No resource de
    teste (mock do API server), criar o Endpoints por `.endpoints().inNamespace().resource(ep).
    create()` **às vezes não destrava a descoberta** (o Stork continua sem instâncias); a forma
    determinística é `client.resource(ep).create()` com o Endpoints já carregando o namespace.
    Registrado em `testing.md` (backend K8s).
  - **`Dockerfile.jvm` versionado vs. JDK do build: acoplamento frouxo.** O extension
    `quarkus-container-image-docker` usa o `Dockerfile.jvm` do módulo, e o base default segue o
    JDK do build — sem `maven.compiler.release` fixo, a compilação mira o JDK da máquina (21 no
    ambiente), então ter `ubi9/openjdk-17-runtime` versionado (caso de reservation/inventory)
    produz imagem que não carrega as classes (major 65 vs 61). O base de todos é
    `ubi9/openjdk-21-runtime` (baseline Java 21) — ADR 011.
  - **Nome de imagem ≠ nome de serviço.** `quarkus.container-image` herda
    `quarkus.application.name` quando setado; nos publishers (`reservations`/`rentals`, ADR 010)
    isso misturaria imagem com catálogo. Fixamos `quarkus.container-image.name=${project.artifactId}`
    para a imagem ser sempre `acme/<módulo>:<version>`, e o nome de catálogo continuar
    `application.name` (ADR 011 §1).
  - **Compose com rede ipam custom não registra aliases implícitos (compose v5).** Na rede
    default com `ipam.subnet` custom, os nomes de serviço (`kafka`, `consul`, `keycloak`,
    `postgres`, `reservation-postgres`, …) **param de resolver** — o compose v5 não injeta os
    aliases implícitos. Quebra o KRaft (`1@kafka:29093`), o Keycloak (`KC_DB_URL`→`postgres`) e
    os apps. Correção: `networks.default.aliases` explícitos por serviço (compromisso do smoke,
    ADR 011 §2). A rede default `172.18.0.0/16` também colide com rota estática da VPN do host —
    o compose declara `172.28.0.0/16`.
  - **Testcontainers: o subnet do broker Kafka é determinístico, não a sorte do Docker.** O
    `Network.SHARED` do `StrimziKafkaContainer` recebe o primeiro `/16` livre (172.18, logo após
    o bridge default 172.17); a mesma rota estática da VPN que o compose encontrou (item acima)
    sequestra esse range → `ip route get <container-ip>` devolve a interface do túnel e o
    AdminClient de `KafkaCompanionResource` morre com `TimeoutException` em `fetchMetadata`.
    Cada módulo Kafka fixa a rede: `Network.builder().createNetworkCmdModifier(
    cmd.withIpam(... subnet 172.29 billing / 172.30 inventory ...))` no `createContainer` —
    subnet própria por módulo permite rodar as duas suítes Kafka em paralelo (ADR 011 §2).

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
| Tracing ponta a ponta (propagação via Kafka) | `quarkus-opentelemetry` nos dois serviços; propagação automática (guia Messaging, seção OpenTelemetry Tracing) | `VehicleRegisteredTracePropagationIntegrationTest` (inventory), `BillingTracePropagationIntegrationTest` (billing) |
| Fault tolerance (SmallRye FT) | reservation `adapter/out/inventory/GraphQLInventoryGateway.java`, `adapter/out/rental/RentalRestGateway.java`, `adapter/in/rest/InventoryUnavailableMapper.java`, `application/exception/InventoryUnavailable.java` | `GraphQLInventoryClientFailureTest` (3, taxonomia medida contra o cliente real), `GraphQLInventoryGatewayFaultToleranceTest` (6), `RentalRestGatewayFaultToleranceTest` (5), `AvailabilityThroughInventoryChainTest` (3, cadeia com o gateway em CDI), `ReservationWriteFailureTest` (3, contrato de falha da escrita), `AvailabilityUnavailableTest` (3), `FindAvailableVehiclesTest` (3, sendo 2 do item 8), `StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice` (1) |
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

### 2.1.5 Tracing ponta a ponta: propagação automática, sem porta

O tracing **não** ganhou porta da aplicação — é efeito de plataforma, como health. Com
`quarkus-opentelemetry` no pom, o Quarkus instrumenta HTTP, Kafka e Reactive Messaging e
propaga o contexto automaticamente (guia oficial "Messaging", seção OpenTelemetry Tracing):

- **Saída (inventory):** a mutation GraphQL `register` roda sob um trace; o record
  `vehicle-registered` produzido carrega o header `traceparent` do span corrente.
- **Entrada (billing):** o consumidor de `vehicle-registered` herda o span do record como pai
  e processa o evento sob o mesmo trace.

Configuração mínima nos dois serviços:

```properties
# Cap.10 - tracing ponta a ponta (item 7).
quarkus.application.name=<billing-service|inventory-service>
%test.quarkus.otel.exporter.otlp.enabled=false
%test.quarkus.otel.simple=true
```

**Por que importa:** regra 9 do AGENTS.md vale para **regra de negócio**; tracing é
infraestrutura transversal que o runtime já faz — criar porta seria desacoplar de nada
(mesma lógica da decisão de health). O contexto viaja no header, nunca no
payload: o contrato do evento (payload) não muda. Em dev, o Dev Service LGTM (Grafana+Tempo)
sobe sozinho para visualizar os traces; em teste, o exporter OTLP fica desligado e um bean
CDI de teste (`InMemorySpanExporter`, padrão oficial "Using CDI to produce a test exporter")
recebe os spans — `simple=true` exporta na hora, sem esperar o batch de 5s. Em prod/docker o
exporter OTLP fica **desligado** (não há collector no compose; ver [deployment.md](../deployment.md#observabilidade-cap10)).

**Escopo atual:** o tracing cobre o salto Kafka inventory→billing. Os hops REST/GraphQL
(users→reservation, reservation→inventory/rental) não são tracejados porque esses serviços não
têm o extension — o item 7 evidencia a capacidade de propagação no pipeline de mensageria, não
o mesh inteiro.

### 2.1.6 Fault tolerance na fronteira de saída: retry onde é seguro, sinal onde não há resposta

O `reservation-service` tem duas chamadas síncronas de saída, e elas têm políticas opostas
porque têm naturezas opostas.

**Escrita (`POST /rentals`) — só timeout.** `RentalRestGateway.start`:

```java
public static final long WRITE_DEADLINE_MILLIS = 2_000;

@Override
@Timeout(value = WRITE_DEADLINE_MILLIS, unit = ChronoUnit.MILLIS)
public Uni<Void> start(String customerId, Long reservationId) {
    return client.start(customerId, reservationId).replaceWithVoid();
}
```

- **Sem `@Retry`**: `StartRental` sempre salva uma nova locação e nunca consulta
  `findByCustomerAndReservation` antes de gravar. Repetir depois de um resultado incerto
  duplica a locação — `StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice`
  é o teste que caracteriza isso e que falha se um dia mudar.
- **Sem `@Fallback`**: o fallback honesto da escrita é não existir. Devolver sucesso confirmaria
  ao cliente que a locação começou, sem locação registrada.

**Leitura (GraphQL `allCars`) — timeout, retry seletivo e fallback que sinaliza.**
`GraphQLInventoryGateway.findVehicles` usa a **mesma lista de falhas** em `@Retry.retryOn` e
`@Fallback.applyOn`, para que "o que pode ser repetido" e "o que pode virar indisponível" sejam
uma decisão só:

A lista foi **medida contra o cliente typesafe real** (`GraphQLInventoryClientFailureTest`, com
um servidor HTTP de verdade), não deduzida da documentação — a primeira versão deste capítulo
usava tipos JAX-RS (`ProcessingException`, `ServerErrorException`) que este cliente nunca lança,
o que tornava retry e fallback letra morta:

| Falha | Retry | Vira `InventoryUnavailable` | Por quê |
|---|---|---|---|
| `TimeoutException` | sim | sim | deadline estourado: pode ser transitório |
| `InvalidResponseException` | sim | sim | resposta HTTP sem envelope GraphQL (inventory reiniciando, proxy, URL errada) |
| `IOException` | sim | sim | conexão recusada/resetada |
| `GraphQLClientException` | não | não | inventory respondeu 200 com `errors`: é defeito do outro lado, não indisponibilidade |
| `InvalidResponseException` (resposta não mapeável) | sim | sim | mesma exceção de "sem envelope GraphQL": o cliente não separa "HTTP sem envelope" de "envelope que não mapeia", e ambos viram indisponibilidade |

Não existe `MappingException` no `smallrye-graphql-client` 2.18.5 (verificado no classpath de
compilação): falha de mapeamento é lançada como `InvalidResponseException`. O cliente typesafe
também não separa "resposta HTTP sem envelope GraphQL" de "envelope GraphQL válido que não
mapeia" — os dois são `InvalidResponseException`. A consequência é que uma falha de mapeamento,
determinística e defeito nosso, também é repetida 3 vezes e convertida em 503. Aceitamos porque
separar exigiria inspecionar o corpo da resposta, mais frágil ainda. E o inverso também vale:
`UnexpectedCloseException` (conexão fechada no meio da resposta) é subclasse de
`InvalidResponseException`, então entra na mesma política de graça.

Duas consequências da medição: falha de I/O chega como `IOException` cru (o Mutiny remove o
embrulho de `CompletionStage` antes de emitir, então `retryOn` casa direto — observar isso por
`await()` daria uma forma de falha do próprio teste, não do cliente), e `InvalidResponseException`
não distingue 5xx de 4xx — uma URL de catálogo errada (404) é repetida 3 vezes antes de virar 503.

O fallback **não devolve lista vazia**. Devolve `InventoryUnavailable`
(`reservation-service/.../application/exception/InventoryUnavailable.java`), falha de aplicação
que distingue "não há veículo" de "não deu para saber":

```java
public final class InventoryUnavailable extends RuntimeException {
    public InventoryUnavailable(Throwable cause) { super("inventory is unavailable", cause); }
}
```

`InventoryUnavailableMapper` (adapter **inbound**) é quem decide o formato visível ao cliente —
503, `Retry-After: 30`, corpo `{"code":"INVENTORY_UNAVAILABLE",...}` — e a causa original fica
só no log. Regra 10 do AGENTS.md: a fronteira traduz, a regra de negócio fica na aplicação.

**Por que a política fica no adapter.** As anotações descrevem a fronteira técnica (o transporte
falhou, o destino está fora), não regra de negócio. Nenhuma porta do `application/port/out`
ganhou anotação de fault tolerance, e nenhum caso de uso mudou de assinatura: `findVehicles()`
continua devolvendo `Uni<List<AvailableVehicle>>`, que agora pode falhar — e falhar é
informação legítima para quem consulta.

**Duas camadas de prazo na escrita, por causa do cancelamento:**

```properties
quarkus.rest-client."org.acme.reservation.adapter.out.rental.RentalClient".connect-timeout=500
quarkus.rest-client."org.acme.reservation.adapter.out.rental.RentalClient".read-timeout=1500
```

O `@Timeout` do SmallRye FT em método que devolve `Uni` **não cancela** a subscription a
montante (medido: um emitter que só termina por cancelamento continuava vivo depois do
deadline; agora versionado como
`RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteCallInFlightWhenTheFaultToleranceDeadlineFires`).
Atenção ao nome: `read-timeout` **não** é deadline absoluto — o padrão do Quarkus para ele é 30
s, e é inatividade com rearmamento, não prazo total. Ele vira `HttpClientRequest.setTimeout`,
que o javadoc do Vert.x chama de `idleTimeout` "com nome confuso" e marca como `@Deprecated`,
**rearma** a cada chunk recebido, é cancelado quando chegam os headers, e deixa o
`idleTimeout` do socket em `0`. Limita a fase de connect (500 ms, via `connect-timeout`) e a
espera por headers; **a leitura do corpo não tem prazo** neste cliente. Sem o ajuste, a
requisição de `POST /rentals` ficaria em voo muito depois do deadline. `RentalRestGatewayFaultToleranceTest
.shouldKeepTheWriteDeadlineAboveTheTransportTimeout` compara os dois prazos e falha se a
ordem se inverter — é uma **guarda de configuração**, não a prova do abort (essa vem de
integração com o container fora do ar, pendência aberta no roadmap do cap. 10). A escrita não tem override por perfil
justamente para essa relação valer em qualquer ambiente.

**Configuração** — política na annotation, valor operacional na config:

```properties
%prod.quarkus.fault-tolerance."org.acme.reservation.adapter.out.inventory.GraphQLInventoryGateway/findVehicles".timeout.value=3
%prod.quarkus.fault-tolerance."org.acme.reservation.adapter.out.inventory.GraphQLInventoryGateway/findVehicles".timeout.unit=SECONDS
```

O identificador é `<classe>/<método>`. A armadilha está na unidade: `timeout.value` **sem**
`timeout.unit` herda a unidade da annotation (`Timeout.unit`, padrão `MILLIS`) — e como a
unidade da annotation é escolha nossa, o mesmo número muda de significado conforme ela
(`300` = 300 ms com annotation em milissegundos, 300 s com annotation em segundos; medido).
Por isso `timeout.unit` vem sempre explícito, e as classes de teste **medem o tempo decorrido**
com limites bilaterais e `@Timeout` de classe — se a chave parar de valer, o teste falha em
segundos, em vez de passar em silêncio ou arrastar a suíte por minutos.

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

Tracing (item 7) — o extension que liga a propagação automática de contexto no Kafka:

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-opentelemetry</artifactId>
</dependency>
```

Nos testes, o exporter em memória vem de `io.opentelemetry:opentelemetry-sdk-testing`
(scope `test`), seguindo o padrão oficial "Using CDI to produce a test exporter".

Fault tolerance (item 8) — a annotation `@Timeout`/`@Retry`/`@Fallback` vem do MicroProfile
Fault Tolerance, e a extensão `quarkus-smallrye-fault-tolerance` traz **a API e a
implementação**: sem ela as annotations nem estão no classpath de compilação (`dependency:tree`
mostra `microprofile-fault-tolerance-api` só por este caminho):

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-smallrye-fault-tolerance</artifactId>
</dependency>
```

O `reservation-service` recebeu a extensão porque é onde a política de fault tolerance deste
capítulo está implementada. O `users-service` também tem chamada síncrona de saída e está
**sem** prazo nem política — dívida declarada na [ADR 009](../adr/009-fault-tolerance-chamadas-externas.md).
Versões resolvidas: `quarkus-smallrye-fault-tolerance` 3.39.3, SmallRye Fault Tolerance 6.11.2,
MicroProfile Fault Tolerance API 4.1.2.

### 2.3 Dependências

| Serviço | `quarkus-micrometer` | `quarkus-micrometer-registry-prometheus` | `quarkus-smallrye-health` | `quarkus-opentelemetry` | `quarkus-smallrye-fault-tolerance` |
|---|---|---|---|---|---|
| billing | ✅ | ✅(cap.10 item 6) | ✅ | ✅(cap.10 item 7) | — |
| inventory | ✅ | ✅ | ✅ | ✅(cap.10 item 7) | — |
| reservation | ✅ | — | ✅ | — | ✅(cap.10 item 8) |
| rental / users | ✅ | — | ✅ | — | — |

---

## 3. Divergências do livro

| # | O que o livro faz | O que fazemos | Por quê | Onde está registrado |
|---|---|---|---|---|
| 1 | Métrica de negócio como classe Micrometer espalhada no código | porta da aplicação + adapter Micrometer | regra 9 do AGENTS.md: regra de negócio ≠ efeito observável; DDD | §2.1.1, 2.1.2 |
| 2 | Livro usa nomes/versões do Quarkus 3.15.1 | API conferida contra 3.39.3 | regra 16 do AGENTS.md | roadmap `[3.39.3]` |
| 3 | Health/metrics "de módulo" sem separar dependência | decisão explícita de itens 1–5: **sem** abstração própria para o que é padrão; abstração só onde é porta da aplicação | ddd-tdd-standards §9 | roadmap cap. 10 |
| 4 | Adapter de métrica só implementa a porta que usa | o gauge de backlog do billing **inverte a direção**: `MicrometerOutboxMetrics` chama `OutboxEventStore.countPending()` (porta que ele não implementa) | exceção billing-specific documentada em ddd-tdd-standards §5-Observability ("Recorded exception") e decisão 15 do architecture.md; só billing tem relay com backlog | §2.1.4, §3 |
| 6 | Livro decora o bean **inbound** com `@Retry` (`CoffeeResource`, §10.5.1) | a FT vive no **adapter de saída** (`RentalRestGateway`, `GraphQLInventoryGateway`), porque é lá que se sabe se a chamada é idempotente — informação que o inbound não tem | regra 10 do AGENTS.md: o inbound mapeia transporte, não decide política; a decisão está na ADR 009 | §2.1.6 + ADR 009 |
| 5 | Livro cobre tracing com propagação HTTP e um Jaeger externo gerenciado à mão (docker run) | propagação **automática** do Quarkus 3.39.3 com `quarkus-opentelemetry` (guia Messaging, seção OpenTelemetry Tracing): via Kafka no nosso pipeline; em dev o Dev Service LGTM sobe sozinho; em teste, exporter CDI em memória | regra 16: API verificada na doc oficial; o livro nem menciona `TracingMetadata` nem exige instrumentação manual no caso Kafka | §2.1.5 + `VehicleRegisteredTracePropagationIntegrationTest` + `BillingTracePropagationIntegrationTest` |

---

## 4. Testes: camada por camada

| Camada | Teste criado | O que prova | Como roda |
|---|---|---|---|
| Application | `PublishPendingOutboxEventsTest` | contadores no caminho de sucesso e de falha; retry x falha | `./mvnw -pl billing-service test -Dtest=PublishPendingOutboxEventsTest` |
| Adapter | `MicrometerOutboxMetricsTest` | counter publicado/falhas, gauge de backlog via `refreshBacklog()` | `./mvnw -pl billing-service test -Dtest=MicrometerOutboxMetricsTest` |
| Integration | `OutboxMetricsIntegrationTest` | `/q/metrics` real: counter >= 1, gauge, client metric Kafka (lag), `quarkus_messaging_message_count_total{channel="invoice-opened-out"}` | `./mvnw -pl billing-service test -Dtest=OutboxMetricsIntegrationTest` |
| Integration (tracing) | `VehicleRegisteredTracePropagationIntegrationTest` (inventory) | o record produzido pela mutation GraphQL carrega o header `traceparent` bem formado, com traceId de um span do próprio serviço | `./mvnw -pl inventory-service test -Dtest=VehicleRegisteredTracePropagationIntegrationTest` |
| Integration (tracing) | `BillingTracePropagationIntegrationTest` (billing) | consumidor processa o evento sob o trace propagado no header do record (traceId do header == traceId do span) | `./mvnw -pl billing-service test -Dtest=BillingTracePropagationIntegrationTest` |

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

### 5.4 Exemplar OTel quebrava o matcher exato do contador

- **Sintoma:** `BusinessMetricsIntegrationTest` (inventory) passou a falhar depois de adicionar
  `quarkus-opentelemetry`, com `AssertionFailedError` e o corpo do `/q/metrics` na mensagem.
- **Causa:** com o extension presente, o Micrometer anexa **exemplars OTel** às linhas de
  contador no formato Prometheus: `inventory_vehicles_registered_total 1.0 # {span_id="...",trace_id="..."} 1.0 <ts>`.
  O matcher `line.matches(name + " [0-9.]+")` (linha inteira) deixava de casar.
- **Correção:** trocar por `Pattern.compile(name + "\\s+([0-9.]+)")` + `matcher.find()` — o
  `find()` casa o primeiro número após o nome e ignora o sufixo do exemplar (mesmo padrão que o
  `OutboxMetricsIntegrationTest` do billing já usava).
- **Prevenção:** ao adicionar tracing, revisar matchers de métricas que usam `matches()` de
  linha inteira; preferir `find()` com `Pattern`.

### 5.5 Warning benigno `io.opentelemetry.usage` no boot

- **Sintoma:** todo boot com `quarkus-opentelemetry` loga
  `WARNING [io.opentelemetry.usage] OpenTelemetry API usage issue detected`.
- **Causa:** o SDK de telemetria do Micrometer/Outbox usa a API OTel de uma forma que o módulo
  de uso registra como não recomendada (ex.: chamar a API global fora do contexto autocapturado).
- **Correção:** nenhuma — é um aviso do próprio SDK, não uma falha de configuração nossa.
- **Prevenção:** não investigar de novo; se algum dia uma das métricas parar de sair, aí sim o
  warning vira sintoma. Fonte: logs de boot dos testes/`quarkus:dev` após o cap.10 item 7.

---

## 6. Conceitos que NÃO usamos (e por quê)

| Conceito do capítulo | Por que não usamos | Quando reavaliar |
|---|---|---|
| SmallRye Metrics (MP Metrics antigo) | `quarkus-smallrye-metrics` está deprecated; Micrometer é o caminho | nunca |
| Abstração própria de health | health é padrão do runtime; abstrair seria desacoplar de nada | nunca (regra) |
| Métrica de negócio fora de porta | conflita com regra 9 do AGENTS.md | nunca (regra) |
| Circuit breaker e bulkhead/limitador de concorrência | o capítulo (§10.5.2) os apresenta como alternativas ao retry; adiados porque ainda não há taxa de erro nem concorrência medida no caminho — só billing e inventory expõem `/q/metrics`, e sem consumidor não há dado | quando o reservation-service expor `/q/metrics` com consumidor; motivo e reavaliação na ADR 009, "Alternativas consideradas" |

---

## 7. Checklist de estudo

- [x] 5 perguntas que eu deveria saber responder depois de ler o capítulo:
  1. Por que liveness, readiness e startup são grupos diferentes?
  2. Qual a diferença entre Counter e Gauge, e onde o backlog da outbox se encaixa?
  3. Quando `/q/metrics` existe e quando não?
  4. Como o client Kafka (lag) e as métricas por canal chegam ao scrape?
  5. Por que métrica de negócio fica atrás de uma porta da aplicação?
- [ ] 3 perguntas que não sei responder (viram tarefa):
  1. ~~Como o contexto de tracing viaja pelo Kafka (headers) sem vazar para o payload?~~
     → respondida no item 7: header `traceparent`, propagação automática (§2.1.5)
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

- [x] roadmap.md com status e evidência executável dos itens 1–10
- [x] service discovery (Stork/Consul) — item 9 — : Stork somente nas saídas REST Client; a
      **publicação** é por adapter próprio (`acme.consul.registration.*` +
      `ConsulServiceRegistration` com health check absoluto e dereg no shutdown), a **resolução**
      é `stork://<nome>`, e instâncias deregistram no shutdown. A saída GraphQL (inventory) não é
      coberta pelo Stork e vira **divergência documentada**. Graceful shutdown ainda pendente — 🔜
- [x] configuração cloud-native — item 10 —: imagem pré-construída imutável
      (`acme/<módulo>:<version>`, Maven profile `docker`, compose `image:` + `pull_policy: never`)
      com runtime por `QUARKUS_PROFILE` (`%docker` Consul / `%kubernetes` Stork-k8s / `%prod`
      host); seleção de testes de discovery por tag (`consul`/`kubernetes`, surefire +
      `-P kubernetes`) e manifests K8s versionados em `others/k8s/`. Smoke do compose verde
      (5 healths + Consul `passing`); ADR 011 registrada.
- [x] suíte do billing verde (62 testes)
- [x] suíte do inventory verde (45 testes)
- [x] suíte do reservation verde (45 testes) depois do item 9 (registro no boot com check
      passing e dereg no shutdown + teste de Consul inalcançável não derrubar o boot; antes do
      item 9: 42 após o item 8)
- [x] ADR do item 8 registrada (`adr/009-fault-tolerance-chamadas-externas.md`)
- [x] ADR 010 do item 9 registrada (`adr/010-service-discovery.md`): descoberta Stork + publicação
      por adapter próprio, com os dois defectos do auto-registro Stork 3.39.3 documentados
- [x] ADR 011 do item 10 registrada (`adr/011-imagens-e-perfis-cloud-native.md`): imagem
      pré-construída imutável (profile docker), runtime por `QUARKUS_PROFILE` e manifests K8s
      versionados — com os achados do smoke (aliases DNS do compose v5, `billing-postgres`,
      base image 21) documentados
- [x] guardiões do item 8 — DDD, TDD e arquitetura rodaram; achados corrigidos

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
[x] índice + roadmap + README da docs atualizados (itens 7 e 8)
[x] suíte verde
[x] guardians rodados (4) e fixes de sincronização aplicados após o fecho
```

---

**Veja também:** [README.md](./README.md) ·
[09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md) ·
[13-transactional-outbox.md](./13-transactional-outbox.md) ·
[../adr/009-fault-tolerance-chamadas-externas.md](../adr/009-fault-tolerance-chamadas-externas.md) ·
[12-modelo-para-novos-capitulos.md](./12-modelo-para-novos-capitulos.md)

---

_Última atualização: 2026-10-08 (cap. 10 itens health 1–5, métricas do pipeline/relay da outbox — item 6 —, tracing ponta a ponta — item 7 —, fault tolerance nas chamadas externas do reservation — item 8 —, service discovery via Stork/Consul — item 9 — e configuração cloud-native — item 10 — concluídos; snippets e seção 1.7 alinhados ao código real; graceful shutdown ainda pendente)._