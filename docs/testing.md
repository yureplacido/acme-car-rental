# Testes — ACME Car Rental

> Estratégia única para todo o repositório. Cada serviço aplica as camadas de teste de acordo com seu papel.
> Para o **porquê** de cada regra (e as lições das armadilhas reais), ver a base de estudo:
> [knowledge/04-estrategia-de-testes-do-projeto.md](./knowledge/04-estrategia-de-testes-do-projeto.md)
> e [knowledge/11-armadilhas-e-licoes.md](./knowledge/11-armadilhas-e-licoes.md).

## Pirâmide de testes

~~~text
              Integration / Native
                     ▲
             Adapter / Contract
                     ▲
            Application Use Cases
                     ▲
               Domain tests
~~~

### Domain
JUnit puro.

Sem Quarkus, CDI, banco, HTTP, GraphQL, gRPC, Panache ou Mutiny.

Objetivo: provar invariantes e comportamentos do domínio.

Exemplos atuais:
- inventory-service/src/test/java/org/acme/inventory/domain/VehicleTest.java
- reservation-service/src/test/java/org/acme/reservation/domain/ReservationTest.java
- rental-service/src/test/java/org/acme/rental/domain/RentalTest.java
- billing-service/src/test/java/org/acme/billing/domain/InvoiceTest.java

### Application
Testes JVM dos casos de uso usando ports/fakes/mocks.

Exemplos atuais: RegisterVehicle, CreateReservation, StartRental, CreateInvoice, ReservationFacade.

O teste deve poder rodar sem subir a aplicação Quarkus.

### Adapter
Usar @QuarkusTest quando a fronteira/framework é parte do comportamento.

Exemplos: Reservation REST, Reservation persistence reativa, GraphQL/gRPC contracts e OIDC/security.

### Observability
O teste de um adapter de métrica segue **duas camadas**, nunca uma:

1. **Lógica do adapter** (contadores, gauge, regra no-caminho-de-falha) = **JUnit puro** com um
   `SimpleMeterRegistry` real, sem Quarkus. Exemplos: `MicrometerOutboxMetricsTest`,
   `MicrometerInventoryMetricsTest` (`knowledge/04` §9, "Adapter unit").
2. **Evidência de ponta a ponta** (o registro é exposto) = `@QuarkusTest` que scaneia o
   `/q/metrics` real, exigindo `quarkus-micrometer-registry-prometheus` no pom do serviço.
   Série é **nome + tag** (não `startsWith` do nome). Exemplos: `OutboxMetricsIntegrationTest`,
   `HealthEndpointTest` (inventory).

Regras: nunca `await().indefinitely()` sob `@RunOnVertxContext` (usar `UniAsserter`); scrape
HTTP de um endpoint em worker thread exige `.emitOn(eventLoop)` para o próximo passo reativo
voltar à event loop; métrica de negócio é sempre declarada como **porta** no serviço — o teste
de application usa o fake da porta.

### Tracing
O teste de propagação de contexto usa o padrão oficial "Using CDI to produce a test exporter":
`io.opentelemetry:opentelemetry-sdk-testing` (scope `test`) + um bean `@Produces @Singleton`
de `InMemorySpanExporter` em `src/test` (o Quarkus usa exporters CDI quando
`quarkus.otel.traces.exporter=cdi`, o default). Em `%test` desligar o exporter OTLP
(`%test.quarkus.otel.exporter.otlp.enabled=false`) e usar `%test.quarkus.otel.simple=true`
(exporta na hora, sem esperar o batch de 5s).

- **Saída (inventory):** `@QuarkusTest` executa a mutation GraphQL, consome o record do broker
  com `KafkaCompanion` e verifica o header `traceparent` bem formado + traceId de um span do
  próprio serviço. Exemplo: `VehicleRegisteredTracePropagationIntegrationTest`.
- **Entrada (billing):** `@QuarkusTest` publica um record com `traceparent` conhecido
  (upstream simulado) e verifica no `InMemorySpanExporter` que o processamento gerou um span
  com o traceId do header. Exemplo: `BillingTracePropagationIntegrationTest`.

Atenção: com `quarkus-opentelemetry` presente, o Micrometer anexa **exemplars OTel** às linhas
de contador na saída **OpenMetrics 1.0** (o que o `/q/metrics` devolve sem `Accept: text/plain`;
o formato Prometheus 0.0.4, pedido com `Accept: text/plain`, não tem exemplars):
`name 1.0 # {span_id=...,trace_id=...} 1.0 <ts>`. Matchers de métrica devem usar `Pattern` +
`find()` (primeiro número após o nome), nunca `matches()` de linha inteira.

### Fault tolerance
Cenário de falha de fronteira é `@QuarkusTest` com `@InjectMock` do **client externo**, e o
comportamento é medido em tentativas — não em mensagem de log:

- **Leitura com retry:** o mock conta chamadas e devolve falha transitória nas N primeiras;
  a asserção é o número de tentativas e a recuperação (`GraphQLInventoryGatewayFaultToleranceTest`).
- **Falha determinística:** erro de GraphQL (o inventory respondeu 200 com `errors`) não é
  repetido nem convertido em indisponibilidade; a asserção é "uma tentativa só".
- **Taxonomia medida, não presumida:** os tipos que a policy declara são os que o cliente real
  lança (`GraphQLInventoryClientFailureTest`). A primeira versão declarava tipos JAX-RS que o
  cliente GraphQL typesafe nunca lança — e o teste passava, provando que a policy não fazia nada.
- **Fallback:** quando as tentativas acabam, a asserção é o **tipo de falha** que chega no
  chamador (sinal de aplicação), e nunca uma lista vazia.
- **Deadline:** o teste mede o tempo decorrido, com limites bilaterais (`>=` o prazo esperado e
  `<` o teto) e `@Timeout` de classe. Isso não é vaidade: `timeout.value` sem `timeout.unit`
  herda a unidade da annotation, então o mesmo número pode valer 300 ms ou 300 s, e uma chave de
  config errada falha em silêncio. Medir o tempo transforma esse silêncio em asserção, e o
  `@Timeout` de classe evita que o engano vire uma suíte de minutos.
- **Prazo de transporte vs. deadline de FT:** o `@Timeout` do SmallRye FT em método que devolve
  `Uni` não cancela a chamada a montante, então a escrita também tem prazo de transporte
  (`quarkus.rest-client."<cliente>".read-timeout`) e um teste compara os dois
  (`RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteDeadlineAboveTheTransportTimeout`, uma
  guarda de configuração — o abort em voo não é provado por aqui e permanece como dívida
  aberta (roadmap/knowledge: o `read-timeout` é inatividade com rearmamento, e a prova do
  cancelamento exige integração real).
- **Taxonomia caracterizada antes da política:** quando a `@Retry`/`@Fallback` depende do tipo de
  falha que o **cliente** lança, esse contrato se mede em JUnit puro com socket real
  (`GraphQLInventoryClientFailureTest`), sem Quarkus — o que se mede é a biblioteca, não a CDI. E
  a falha é observada **por assinatura**: `await().indefinitely()` re-empacota exceção checada em
  `CompletionException`, e atribuir essa forma ao cliente faria a `@Retry` parecer aplicada sem
  estar.
- **Exceção medida: o MP REST Client exige boot.** `ReservationsClientFailureTest` (BFF) mede a
  mesma coisa — tipos que o cliente real lança contra um servidor HTTP de verdade —, mas
  **precisa de `@QuarkusTest`**: o `RestClientBuilder` resolve o builder via
  `RestClientBuilderResolver`, e o `resteasy-reactive-client` não registra implementor fora do app
  em execução (sem `META-INF/services/org.eclipse.microprofile.rest.client.spi.RestClientBuilderResolver`
  e sem `static` que o instale). Sem boot a sonda falha com
  `IllegalStateException: No RestClientBuilderResolver implementation found!` — verificado tirando
  a anotação e rodando a classe, não presumido. A regra "sem Quarkus" vale para o cliente que
  *sobe sozinho* (SmallRye GraphQL); para este, isolamento não é negociável. O mesmo bloqueio já
  estava documentado na §Service discovery (`@AccessToken` aborta fora de request): a sonda em
  `src/test` contorna o token, mas não contorna o boot.
- **Os prazos de transporte do BFF são duas chaves e uma separação.** `quarkus.rest-client.<configKey>.*`
  é configuração **por cliente**, não por método — por isso a leitura tem configKey própria
  (`reservations-availability`) e a escrita não idempotente fica fora do alcance do `read-timeout`
  curto. As duas metades têm guarda: `shouldBoundTheAvailabilityReadWithTransportTimeouts`
  (chaves existem e são curtas) e
  `shouldNotPutTheAvailabilityDeadlineOnTheNonIdempotentWrite` (a escrita segue o default).
  Ambas são guarda de configuração, não prova de comportamento: provar que o cliente **injetado**
  recebe os valores é o abort em voo, ainda em aberto (ver abaixo).
- **Limite conhecido do contrato HTTP do BFF:** `ReservationsAvailabilityUnavailableTest` roda em
  `%test.quarkus.http.auth.permission.available.paths=available`, que libera o `GET /available` sem
  OIDC justamente para o teste HTTP ser possível (`@QuarkusTest` não autentica o browser). Consequência:
  o ramo "401 de token propagado mascarado como 503" (consequência da ADR 009 §6) **não é exercitado**
  — em `%prod`/`%docker` o endpoint continua atrás do login. Não é falha do teste, é o limite do
  perfil: o 401 é da política de segurança, não da fronteira de disponibilidade.
- **Caracterização antes de política:** quando a política depende de uma propriedade do outro
  serviço (aqui, "a escrita não é idempotente"), o teste que fixa essa propriedade fica no
  serviço dono dela (`StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice`).

`@InjectMock` de um REST client MicroProfile exige o qualifier no campo
(`@InjectMock @RestClient RentalClient client`): sem ele a resolução do bean falha, porque
o bean registrado só carrega o qualifier `@RestClient`.

### Service discovery (Stork + Consul)

Estratégia adotada no cap. 10 item 9 (evidência da fronteira, com **Consul real de
testcontainers** — sem mock) e padrões que valem para quem vier a tocar nesse assunto:

- **Prova completa onde dá, resolução pura onde não dá.** O caminho inteiro
  REST Client→Stork→Consul→instância é provado no `reservation-service`
  (`RentalServiceDiscoveryTest`): o `RentalClient` não tem `@AccessToken`, então a chamada
  sai mesmo sem request autenticado. No `users-service` isso é impossível: o `ReservationsClient`
  carrega `@AccessToken`, e o filtro de propagação **aborta com 401** fora de request
  autenticado (`AccessTokenRequestReactiveFilter` sintetiza o 401 quando o token é nulo; o
  producer é request-scoped, roda no event loop, e nem `Arc.requestContext().activate()` no
  thread do JUnit resolve). A prova do BFF fica no nível do **Stork**
  (`Stork.getInstance().getService(...).getInstances().await().indefinitely()`) + requisição
  direta ao endereço resolvido - registrado na javadoc do teste. Não tentar "empurrar" o
  BFF para o cliente OIDC: é comportamento do framework, não da fronteira dele.
- **A mesma restrição reaparece quando se mede a falha do cliente do BFF, e vale o mesmo
  truque.** `ReservationsClientFailureTest` (cap. 10, ADR 009 §6) precisa medir como o
  `AvailabilityClient` classifica cada falha, mas o cliente tem `@AccessToken` e o filtro
  abortaria com 401 antes de a requisição sair. A medida usa uma **interface-sonda equivalente
  em `src/test`** (mesmo `@Path`/método, sem `@AccessToken`): é o mesmo `resteasy-reactive`,
  medindo o mapeamento de erro recebido e de falha de transporte, que não dependem do filtro
  de token. A lição é a mesma do cap. 8/10 — a taxonomia **se mede**, não se deduz da
  documentação, e o atalho de teste é aceitável quando ele mede a mesma coisa.
- **Registro de serviço se testa no boot, com perfil dedicado e assert de "passing".** O
  registro é síncrono no boot (adapter próprio em `adapter/out/registration`, `@Observes
  StartupEvent`/`@PreDestroy`); o `application.properties` desliga em `%test` e o teste
  religa via `QuarkusTestProfile` setando `enabled=true`. Asserções sobre o catálogo: 1
  instância, `Service.Service`/`Port`/`Address` certos e, principalmente, o health check
  com **Status passing** (query `?passing=true`) - presença de `Checks` não basta (ver o
  RED abaixo). Para o Consul alcançar o `/q/health/live` do JVM, o container de teste sobe
  com `withExtraHost("host.docker.internal", "host-gateway")` e o profile aponta o endereço
  registrado para `host.docker.internal`.
- **Porta registrada = listener real.** Com `%test...test-port=0` (aleatória) o valor em
  config continua `0` na hora do `StartupEvent` - e o Consul **omite `Port` do catálogo**
  (omitempty), fazendo o teste estourar NPE. Nos testes de registro a porta é fixa por
  perfil (`quarkus.http.test-port=18081`/`18082`, uma por módulo) e o DEFAULT do registrar
  (a porta HTTP de config) produz a mesma; não inventar porta "só para o catálogo" — senão
  nada escuta nela e o check nunca passa.
- **Poll com deadline, nunca sleep fixo.** Registro acontece no boot e o check leva um
  intervalo (5s) para virar passing; os testes pollam o catálogo com deadline (30s) e
  re-assertam. Os test resources registram/são síncronos (PUT 200) e não precisam retry.
- **RED real do item 9 (regra 17).** Dois defectos do auto-registro do **Stork** na 3.39.3:
  o recorder injeta `health-check-url` **relativa** (o Consul marca critical desde o boot e
  remove do catálogo após o `DeregisterCriticalServiceAfter` default de 1m, então discovery
  com `passing=true` volta 0) e o deregister roda como última shutdown task, **depois do CDI
  fechado**, explodindo com "No CDI container is available" quando o classpath tem
  narayana-jta (caso do rental). Foi o que motivou o adapter próprio
  (`ConsulServiceRegistration`), e os testes atuais negam os dois sintomas (passing + saida
  limpa do catálogo).
- **Sem Dev Service para o Consul, e "Consul fora" tem evidência própria.** `%dev`/prod sem
  Consul em `localhost:8500` não derruba o boot: o registro falha, loga ERROR e o serviço
  continua no ar **mas não é descoberto** (a discovery só aponta em ambientes com Consul).
  Esse contrato é provado por `ConsulRegistrationFailureTest` (em `reservation-service` e
  `rental-service`): perfil aponta para um Consul inalcançável (porta 1), o boot segue, e
  `register()` contém a falha sem estourar. Para "não travar" valer de verdade, as chamadas
  HTTP do adapter têm prazos curtos (connect e request de 5 s), e o deregister trata **404
  como sucesso** (duplo deregister é idempotente — o Consul responde 404 para id já ausente,
  não 200). Em dev use o compose (`--profile services` sobe o Consul) ou rode o Consul do
  catálogo.
- **Dois backends de discovery, seleção por tag — sem pagar o outro backend no baseline.**
  Os testes de descoberta ganharam `@Tag("consul")` (fronteira real, testcontainers) e
  `@Tag("kubernetes")` (Stork-k8s contra **mock do API server** — sem kind/k3s, ver abaixo).
  Cada pom define `acme.test.discovery.excludedGroups=kubernetes` no surefire e o profile
  `-P kubernetes` troca para `consul`; assim o build CI roda o backend Consul e `-P kubernetes`
  valida o Kubernetes com o mesmo classpath. Valores medidos: reservation baseline 45 /
  `-P kubernetes` 43 (o k8s tem 1 teste), users 6/5, rental 10/8, inventory 45, billing 62.
- **O mock do API server substitui o cluster, sem virar testes de Kong.** O resource de teste
  (`KubernetesRentalDiscoveryTestResource` / `KubernetesReservationsDiscoveryTestResource`)
  arranca `KubernetesServer` (fabric8) e injeta recursos com o client do teste; o teste pede
  a instância ao Stork com `use-endpoint-slices=false` (o caminho de Endpoints; medido na
  3.39.3 o provider exige `k8s-namespace` e `targetRef` no EndpointAddress, e no CRUD mock os
  Endpoints só sobem de forma determinística via `client.resource(ep).create()` — o form
  `.endpoints().inNamespace().resource(...).create()` falha em emitir o POST). Isso prova a
  resolução de instância pelo **provider real** sem depender de cluster ambulante.

### Kafka (testcontainers)
As suítes Kafka do billing e do inventory usam **uma** instância do broker Strimzi por JVM de teste
(`KafkaCompanionResource`, `restrictToAnnotatedClass=false`). O broker é fixado numa rede
testcontainers dedicada de **subnet determinística** (`172.29.0.0/16` billing, `172.30.0.0/16`
inventory) em vez do `Network.SHARED` default: o Docker aloca o SHARED como o primeiro `/16`
livre (172.18 após o bridge 172.17) e VPNs corporativas injetam **rotas estáticas** que sequestram
  esses RFC1918 — com o broker num range sequestrado o forwarding host→container da porta publicada
  morre e o AdminClient falha com `TimeoutException` em `fetchMetadata` (medido 2026-10-08: o
  `ip route get 172.18.0.2` devolvia a interface do túnel). Subnets próprias por módulo também
  permitem rodar billing e inventory Kafka em paralelo sem overlap. A medida está replicada em
  `BillingKafkaCompanionResource`/`InventoryKafkaCompanionResource`.

  Os companions precisam ainda de um **nome de rede determinístico**. O builder do testcontainers
  gera um nome aleatório por instância de `Network`; como os testes de config-evidence do cap.11
  (item 11.1) sobem um **segundo** Quarkus app sob o perfil `kubernetes` enquanto o primeiro ainda
  roda, o resource global reinicia e a segunda rede aleatória cairia na mesma subnet, que o Docker
  rejeita com `403 Pool overlaps`. A solução é um `Network` anônimo cujo `getId()` devolve o nome
  determinístico (`acme-car-rental-{billing,inventory}-kafka`): o Testcontainers usa o `id` como
  `networkMode`, então o broker entra na rede já existente em vez de criar outra. A rede é criada
  por `start()` (via `DockerClientFactory.lazyClient()`) só se ausente, com subnet/gateway pinados,
  `withCheckDuplicate(true)` e os labels do `ResourceReaper` (limpeza por Ryuk); se existir com
  subnet divergente e vazia é recriada, se estiver em uso lança `IllegalStateException`.

### Config-evidence do perfil `%kubernetes` (cap.11 item 11.1)
Cada serviço tem um `KubernetesConfigProfileTest` que **sobe o mesmo jar sob o config profile
`kubernetes`** — não basta conferir a chave no `.properties`. O gatilho é um `@TestProfile` cujo
`getConfigProfile()` devolve `"kubernetes"`; os `getConfigOverrides()` só adicionam `plain keys`
de boot-safety (`quarkus.http.test-port=0`, `quarkus.oidc.tenant-enabled=false`,
`quarkus.{hibernate-orm,mongodb,kafka,kubernetes-client}.devservices.enabled=false`, etc.) e
**não** mascaram chaves de provider. O teste então lê a config efetiva
(`ConfigProvider.getConfig().getValue(...)`) e assere o alvo do perfil — ex. datasource
`vertx-reactive:postgresql://billing-postgres:5432/billing`, `mysql://inventory-mysql:3306/inventory`,
`mongodb://rental-mongo:27017`, OIDC `http://keycloak:8080/realms/car-rental`, Stork-k8s. A
asserção de Kafka é tolerante (`assertNotNull`) porque os Kafka companions globais
(`restrictToAnnotatedClass=false`) mascaram `kafka.bootstrap.servers`; a evidência forte fica no
datasource. `quarkus.hibernate-orm.sql-load-script=none` é inválido (gera `ConfigurationException`).

### Integration / native
@QuarkusIntegrationTest é reservado para validar o artefato empacotado e o runtime.

## TDD

Cada mudança de comportamento deve seguir:

~~~text
Specification
     ↓
RED
     ↓
GREEN
     ↓
REFACTOR
~~~

Um teste criado depois da implementação não é evidência suficiente de TDD.

Os commits devem preferencialmente manter essa história visível: test(...), feat(...), refactor(...).

**Dívida declarada do item 10 (backend K8s).** Os testes `@Tag("kubernetes")`
(`RentalServiceDiscoveryKubernetesTest`, `ReservationsServiceDiscoveryKubernetesTest`) foram
escritos GREEN-first: chegaram no mesmo commit que a config `%kubernetes` (a história RED não
fica no git, e `b6c726e` ainda quebrava a compilação — corrigido em `8bd81cd`). É a mesma
exceção já aceita para o item 9 (RED documentado acima, não commitado), agora tornada padrão:
o cenário "nome não resolve" é coberto por asserção (size==1 e host/port do stub), não por um
RED prévio no histórico. Reabilitar RED real fica como dívida aceita.

**Dívida declarada da ADR 009 §6 (users-service, 503 de disponibilidade).** Os testes novos do BFF
(`ReservationsClientFailureTest`, `ReservationsRestGatewayTest`, `ReservationsAvailabilityUnavailableTest`
e o caso novo do `ReservationFacadeTest`) foram escritos GREEN-first na mesma série de trabalho,
mas **o RED principal é demonstrável, não presumido**: o primeiro caso a falhar foi
`ReservationsRestGatewayTest.shouldSignalAvailabilityUnavailableWhenReservationsAnswersAnError`,
porque `ReservationsRestGateway.availability` ainda não tinha `try` — o diff da mudança mostra o
`catch` sendo adicionado, então o teste não passa contra o código anterior. Da mesma forma o
guarda da separação de clientes foi escrito antes dela e falhou por
`cannot find symbol: AvailabilityClient` (observado nesta mudança), e o `assertNotNull` da
caracterização foi trocado por `assertInstanceOf` quando ficou claro que a classe imprimia a
medida sem fixá-la. A diferença para os itens 9/10: aqui o RED foi observado, não arquivado.

O que **não** tem RED é
`ReservationFacadeTest.shouldPropagateAvailabilityUnavailableInsteadOfReturningAnEmptyList`
— a fachada é delegação pura, então o caso não pode falhar por mudança de comportamento; ele é
**guarda** (ninguém captura o sinal e devolve `[]` depois), não evidência de ciclo completo, e
a ADR 009 o lista como evidência de fronteira, não de RED. E
`ReservationsAvailabilityUnavailableTest` depende do Quarkus boot para falar HTTP, então o ciclo
se completa nela como em todo contrato de fronteira: o que a isola de "escrito depois" é o caso
do gateway acima, vermelho contra o código anterior.

## Nomenclatura
Prefira nomes como shouldRejectReservationWhenVehicleIsAlreadyReserved e evite nomes que descrevem implementação.

## Reactive testing
Testes reativos devem provar comportamento da pipeline. Não use await().indefinitely() para esconder um contrato assíncrono em código que deveria permanecer não bloqueante.

Em teste anotado com @RunOnVertxContext (que roda na event loop do Vert.x) é proibido await().indefinitely(): o await bloqueia a thread que precisa entregar o item, gerando deadlock. Nesses casos use UniAsserter/AssertSubscriber, ou isole a operação bloqueante com runSubscriptionOn(...).runSubscriptionOnIn(workerThread). Ver docs/knowledge/04-estrategia-de-testes-do-projeto.md §5.

Para Hibernate Reactive, o Quarkus fornece suporte específico de teste e exige contexto/sessão reativa apropriados. Ver <https://quarkus.io/guides/hibernate-reactive-panache>.

### O que "provar backpressure" significa (cap. 8)

Concorrência limitada e backpressure são **medidas diferentes**, e um teste que só prova a primeira
passa verde sem provar a segunda. O par que separa as duas:

- **requests ao upstream** (`AssertSubscriber.create(0)` + `onRequest().invoke(...)`): mede
  *demanda* — com o `merge`, o upstream recebe no máximo `maxConcurrency` requests.
  (`BulkRegisterVehiclesBackpressureTest`)
- **itens efetivamente produzidos pelo upstream** (`onItem().invoke(...)` contando emissões):
  mede **materialização** — se o pipeline drenasse a fonte, produziria todos os itens mesmo sem
  demanda a jusante. (`BulkRegisterVehiclesMaterializationTest`, fonte de 100 itens)

O segundo tem um **contraponto obrigatório** (`shouldConsumeTheWholeSourceWhenDemandIsUnbounded`):
sem ele, um pipeline que simplesmente não consome nada passaria pelo primeiro por engano. Teste
negativo sem o positivo que o contradiz não prova nada.

**Falha e cancelamento são eixos separados.** A ADR 006 (§Failure policy) exige três coisas da
falha de um item: que ela propague como falha do stream, que os itens já concluídos permaneçam
persistidos (fail-fast não é rollback) e que o cancelamento a jusante seja respeitado. O terceiro
tem dois caminhos distintos, e ambos precisam de teste:

- **cancelamento por decisão do consumidor** — `BulkRegisterVehiclesCancellationTest`: quem
  cancela é o downstream, e as operações em voo recebem o cancelamento;
- **cancelamento por falha de uma irmã** — `BulkRegisterVehiclesMaterializationTest.shouldCancelSiblingsInFlightWhenOneItemFails`:
  quem cancela é a falha. O comportamento do `merge` (cancelar as subscriptions irmã) foi
  **medido**, não presumido — um probe descartável mostrou `started=2 cancelled=1` antes de o
  teste ser escrito.

**Provar que o teste tem força é parte do trabalho.** Um teste novo que passa não prova que mede o
que diz medir. O padrão do repositório é introduzir temporariamente o defeito e ver o teste
falhar com a mensagem esperada — foi assim que se validou o teste de materialização (com
`collect().asList()` antes do processamento, a asserção acusou "produziu 100 de 100"), o de
fail-fast (com a política de erro trocada, a falha deixou de propagar e os 4 itens foram
persistidos) e o de cancelamento das irmãs (com a falha engolida, 2 testes caíram). Nenhuma
mudança de produção foi mantida dessas experiências.

**RED genuíno também acontece na escrita da asserção.** O teste de cancelamento das irmãs falhou
na primeira execução — e a asserção, não o código, estava errada: eu esperava zero gravações,
mas o item em voo já havia alcançado o repositório (o que importa é ter sido *cancelado*, não
gravado). A falha do RED corrigiu a expectativa, e não o código de produção.

---
_Last updated: 2026-10-13 (cap.11 item 11.1: `KubernetesConfigProfileTest` sobe o mesmo
jar sob o config profile `kubernetes` com `getConfigProfile()` e overrides de boot-safety;
Kafka companions com rede determinística nomeada (reuso entre boots, evita `403 Pool
overlaps`; ver §Kafka). Antes, 2026-10-08: service discovery com dois backends testáveis
por tag `consul`/`kubernetes` e subnets pinadas 172.29/172.30 contra rotas de VPN)._
