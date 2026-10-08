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
`BillingKafkaCompanionResource`/`InventoryKafkaCompanionResource` (override de `createContainer`
+ `Network.builder().createNetworkCmdModifier(...)`).

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

## Nomenclatura
Prefira nomes como shouldRejectReservationWhenVehicleIsAlreadyReserved e evite nomes que descrevem implementação.

## Reactive testing
Testes reativos devem provar comportamento da pipeline. Não use await().indefinitely() para esconder um contrato assíncrono em código que deveria permanecer não bloqueante.

Em teste anotado com @RunOnVertxContext (que roda na event loop do Vert.x) é proibido await().indefinitely(): o await bloqueia a thread que precisa entregar o item, gerando deadlock. Nesses casos use UniAsserter/AssertSubscriber, ou isole a operação bloqueante com runSubscriptionOn(...).runSubscriptionOnIn(workerThread). Ver docs/knowledge/04-estrategia-de-testes-do-projeto.md §5.

Para Hibernate Reactive, o Quarkus fornece suporte específico de teste e exige contexto/sessão reativa apropriados. Ver <https://quarkus.io/guides/hibernate-reactive-panache>.
---
_Last updated: 2026-10-08 (service discovery: dois backends de discovery testáveis por tag
`consul`/`kubernetes` com seleção no surefire (`acme.test.discovery.excludedGroups`, profile
`-P kubernetes`), o backend K8s provado com mock do API server sem cluster — armadilha medida
do CRUD de Endpoints no fabric8 —, e o broker Strimzi dos Kafka companions fixado numa rede
testcontainers de subnet determinística (172.29/172.30) porque o `Network.SHARED` default
cai no primeiro /16 livre (172.18), sequestrado por rotas estáticas de VPN corporativa)._
