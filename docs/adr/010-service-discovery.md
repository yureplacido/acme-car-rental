# ADR 010 — Service discovery (item 9): descoberta Stork + Consul, publicação por adapter próprio

- **Status:** Accepted / implementada
- **Data:** 2026-10-07
- **Contexto:** Capítulo 10 — Cloud Native Patterns (service discovery)
- **Escopo:** descoberta de serviços síncronos (`users→reservation` como `stork://reservations`,
  `reservation→rental` como `stork://rentals`) e publicação de `reservation-service`/`rental-service`
  no catálogo Consul

## Contexto

Antes do item 9, a localização de serviço era configuração fixa por ambiente (`REST_URL`,
`INVENTORY_SERVICE_URL`, etc.). O item 9 pede desacoplar localização da configuração: o chamador
resolve a instância de destino num catálogo em runtime. O livro (*Quarkus in Action* 10.6,
p. 301–302) aponta o Stork como a integração nativa do Quarkus, e para produção o service
discovery da **plataforma** (Kubernetes/OpenShift) — aqui a plataforma é o Compose, então o
catálogo é o **Consul** (agente por host + `agent -dev` no compose).

Gate do repositório: framework novo, versão-pinned (`3.39.3`) — a regra 16 exige verificar contra
a versão real. Foi isso que revelou os dois defectos abaixo.

## Restrições que moldam a decisão

- O Stork integra **REST Client e gRPC**; o cliente **GraphQL** (SmallRye GraphQL Client) não
  participa. A saída `reservation→inventory` (GraphQL) não pode entrar no Stork.
- A publicação exige **health check HTTP**: o Consul só trata a instância como sadia se
  conseguir executar o check; a descoberta com `use-health-checks=true` (default) filtra o
  catálogo por `?passing=true`. Instância com check não-passing é "no ar mas não descoberta".
- `DeregisterCriticalServiceAfter` (default 1 min no Consul) remove do catálogo qualquer
  instância com check critical mantido — o silêncio que testamos foi exatamente esse.
- O deregister precisa rodar **dentro** da vida do CDI: no shutdown, CDI já foi fechado quando
  rodam as últimas shutdown tasks do runtime.

## Decisão

| Lado | Mecanismo | Onde |
|---|---|---|
| Descoberta | **Stork**, via `quarkus.stork.<svc>.service-discovery.type=consul` (+ `consul-host`/`consul-port`) | `users-service` e `reservation-service` |
| Publicação | **Adapter próprio** (`adapter/out/registration/ConsulServiceRegistration`), um por serviço que se publica | `reservation-service` e `rental-service` |

### 1. Descoberta por Stork, no prefixo do serviço

O URL do REST client vira `stork://<service>`, e o provider Consul resolve o catálogo. As chaves
entram **diretas no prefixo do serviço**, sem sub-bloco `params`:

```text
quarkus.stork.rentals.service-discovery.type=consul
quarkus.stork.rentals.service-discovery.consul-host=localhost   # %docker → consul (host do compose)
quarkus.stork.rentals.service-discovery.consul-port=8500
```

O artefato é `stork-service-discovery-consul` (BOM 2.7.10 gerenciado pelo Quarkus). Sem ele o
boot falha em augmentação ("config property … is required") — foi o RED do item.

### 2. Publicação por adapter próprio, não pelo auto-registro do Stork

O `stork-service-registration-consul` promete auto-registro. Na **3.39.3 medido** (fontes de
`quarkus-smallrye-stork-3.39.3-sources.jar`) ele tem dois defectos que tornam o mecanismo
inutilizável em produção:

1. **Health-check-url relativa, sem override.** `StorkConfigUtil.addRegistrarTypeIfAbsent`
   faz `parameters.put("health-check-url", …)` **incondicionalmente** quando a capability
   SmallRye Health existe, sobrescrevendo até um override explícito; a URL é derivada de
   `quarkus.management.root-path` + `quarkus.smallrye-health.root-path` + liveness-path →
   **relativa** (`q/health/live`). O Consul não resolve URL relativa — o check nasce
   **critical** desde o boot e a instância é removida do catálogo após o
   `DeregisterCriticalServiceAfter` (1 min). Com descoberta filtrando `passing`, o destino vira
   0 instâncias em `%prod`/`%docker`, em silêncio.
2. **Deregister depois do CDI fechado.** O deregister de Stork roda como última shutdown task,
   depois do Arc desligar; num classpath com narayana-jta (o caso do rental) explode com
   `java.lang.IllegalStateException: No CDI container is available`.

**Decisão:** a publicação é do serviço. `ConsulServiceRegistration` usa a **API HTTP do agente
Consul** (JDK `HttpClient`, payload `io.vertx.core.json.JsonObject`) para registrar no boot
(`@Observes StartupEvent`) com health check HTTP **de URL absoluta**
`http://<address>:<port>/q/health/live`, e deregistra no `@PreDestroy` (garantido pelo container,
antes do CDI fechar). Nome do serviço = `quarkus.application.name` (`reservations`, `rentals`) —
o mesmo nome que o chamador procura em `stork://…`. Falha de registro loga ERROR e **não derruba
o boot** (consciência: serviço "no ar mas não descoberto") — pacto que o código sustenta com
evidência: **prazos HTTP curtos** (connect e request de 5 s, senão o lifecycle poderia travar o
boot/shutdown), exceção de startup/shutdown **contida** e deregister tratando **404 como sucesso**
(duplo deregister é idempotente; medido: o agente responde 404, não 200, para id ausente).

```text
acme.consul.registration.enabled=true                  # %test → false; teste dedicado religa
acme.consul.registration.consul-host=localhost          # %docker → consul
acme.consul.registration.consul-port=8500
acme.consul.registration.address=<detecta IP em prod; localhost/host.docker.internal em dev/test>
acme.consul.registration.port=<Test → quarkus.http.test-port; senão quarkus.http.port>
acme.consul.registration.health-check-path=/q/health/live
acme.consul.registration.health-check-interval=5s
acme.consul.registration.health-check-deregister-after=1m
```

O namespace é **`acme.consul.registration.*`**, não `quarkus.consul.*`: o prefixo `quarkus.consul` é
reservado ao (futuro) `quarkus-consul-client` — usar chaves não registradas do Quarkus dispara o
WARN "Unrecognized configuration key" em todo build e validação por digitação; o prefixo próprio
mata o WARN e neutraliza a colisão caso a extensão entre no classpath.

ID da instância no Consul: `{service}-{address}-{port}` (registro repetido do mesmo ID sobrescreve,
o que mantém os testes idempotentes).

### 3. Escopo explícito (divergência documentada)

`reservation→inventory` (GraphQL) **não** migra para o Stork: o cliente GraphQL typesafe não
participa do Stork, e o provider `consul` não se aplicaria a ele. Segue com URL externalizada
(`INVENTORY_SERVICE_URL`) — mesma divergência que permanece documentada em `services.md` e no
roadmap. **Não-objetivo também explícito:** o `inventory-cli` (cliente administrativo local)
resolve o gRPC por host/porta fixos (`localhost:9000`) — legítimo para uma CLI local; o Stork
suportaria gRPC, mas isso seria descoberta de cluster, fora do item 9.

Regra de build medida na 3.39.3: `SmallRyeStorkProcessor.checkThatJacksonExtensionIsUsedWhenConsulIsOnTheClasspath`
derruba a augmentação se houver provider Consul do Stork (discovery **ou** registrar) e **não**
houver `quarkus-jackson` no classpath. Hoje é satisfeito de passagem
(`quarkus-rest-client-jackson` puxa o Jackson nos consumidores); o próximo módulo que carregar
Stork-Consul precisa saber disso — daí constar aqui e no [knowledge](./14-cloud-native-patterns.md) §1.7.

### 4. Teste de registro negando os dois defectos

`ReservationRegistersInConsulTest` / `RentalRegistersInConsulTest` (1 cada, Consul **real** de
testcontainers com `withExtraHost("host.docker.internal", "host-gateway")`): o `application.properties`
desliga o registro em `%test`, o `QuarkusTestProfile` religa com `address=host.docker.internal` e
**porta HTTP fixa por módulo** (`quarkus.http.test-port=18081`/`18082`), e o catálogo é consultado
por HTTP com deadline. Asserções:

- registro no boot: `Service.Service`/`Port`/`Address` corretos;
- **`?passing=true` devolve 1 instância** (o Consul alcança o `/q/health/live` do JVM) — nega o
  defecto 1;
- após `deregister()`, o catálogo esvazia — nega o defecto 2.

Armadilha fina registrada: com `test-port=0` (aleatória) o valor de config continua `0` no
`StartupEvent` e o Consul **omite `Port` do catálogo** (Go `omitempty`), fazendo o teste estourar
NPE. Daí porta fixa no perfil — e o default do adapter (porta HTTP de config) casa com a mesma.

## Alternativas consideradas

**Auto-registro do Stork com override de `health-check-url`.** Rejeitado: o `put` do recorder é
incondicional na presença da capability de health — não há override na 3.39.3. A "solução" seria
remover `quarkus-smallrye-health`, inaceitável (item 1 do cap. 10 usa `/q/health/live`).

**Registrar via `@Observes ShutdownEvent`.** Rejeitado como garantia: a ordem entre `ShutdownEvent`
e os shutdown tasks do runtime não é contratual; o defecto 2 existe justamente por um chaveamento
de ordem. `@PreDestroy` é o ponto onde o CDI ainda é garantia do container.

**Provider de platforma (Kubernetes/OpenShift).** Fora do escopo da casa: não há cluster; o Compose
é a plataforma e o Consul o catálogo. A regra 16 (verificar contra a versão pinada) valeu aqui por
ter sido justamente a versão, não a escolha de provider, que derrotou o mecanismo do framework.

## Consequências

**Boas.**

- Item 9 fechado com evidência executável que nega os dois defeitos (passing no boot, catálogo
  vazio após deregister) — regra 17 atendida.
- O rental, que **não** tem saída síncrona para descobrir, cai para zero extensões Stork: só os
  consumidores carregam o provider de discovery. Consequência sentida no pom.
- Falha de registro não derruba boot: é observável por log, em `%dev` sem Consul o serviço sobe,
  e o pacto tem teste (`ConsulRegistrationFailureTest`).

**Ruins.**

- O adapter de registro é duplicado entre os dois serviços (mesma classe, package local por
  contexto). Hoje é barato; se um terceiro serviço precisar se publicar, o adapter sobe para um
  módulo/UI comum — decisão protelada de propósito.
- Com o Consul fora (ou o registro falhando), o serviço fica no ar **invisível** no catálogo: a
  descoberta `passing` não o enxerga. É um alerta via log, não via health do próprio serviço.
- **Reinício do Consul é outage silencioso de descoberta:** o registro é boot-once e o catálogo
  do `-dev` é em memória; um reinício do container soma sem instâncias registradas até cada JVM
  reiniciar (cleanup do registro periódico ficaria para um item de saúde de catálogo).
- **`quarkus.application.name` virou identidade dupla de propósito:** para os que se publicam
  (`reservations`, `rentals`) ele **é** o nome no catálogo — e portanto também o rótulo de
  tracing/métricas (`otel.service.name`) desses serviços; inventory/billing mantêm identidade
  artefatual (`inventory-service`, `billing-service`). Antes de um **terceiro** publisher, decidir
  entre manter esta convenção ou separar via `acme.consul.registration.service-name`.
- `%dev` sem Consul pede um `consul` local ou o compose (`--profile services`); é ruído a mais
  para quem roda só o app — registrado em `testing.md`.

## Evidência

| Teste | O que fixa |
|---|---|
| `ReservationsServiceDiscoveryTest` (2) | o BFF resolve `stork://reservations` no catálogo (nível do Stork; o caminho completo é impossível por causa do `@AccessToken`) |
| `RentalServiceDiscoveryTest` (1) | **caminho completo** REST Client→Stork→Consul→stub no reservation→rental (`RentalClient` não tem token) |
| `ReservationRegistersInConsulTest` (1) | `reservations` registra no boot, check **passing** (host-gateway), dereg esvazia o catálogo |
| `RentalRegistersInConsulTest` (1) | `rentals` idem, com o rental sem nenhuma extensão Stork |
| `ConsulRegistrationFailureTest` (1 em cada módulo) | o contrato "Consul fora não derruba o boot nem trava o shutdown": app sobe, `register()` contém a falha, `deregister()` sobe o erro tipado que o `@PreDestroy` captura |

## Relação com outras decisões

- **ADR 004/005 (contratos REST/GraphQL)** — o Stork muda só o endereço do REST client; contrato
  de transporte inalterado. A saída GraphQL permanece na ADR de inventory (URL externalizada) —
  é a divergência que o item 9 documenta.
- **ADR 009 (fault tolerance)** — a descoberta entrega a instância; a política (timeout/retry)
  continua nos adapters de saída, intacta. A dívida do `users-service` (sem tratar 503) permanece
  aberta lá.

## Estado da decisão

**Accepted / implementada**, com evidência na fronteira real (Consul de testcontainers). A
plataforma permanece Consul/Compose; a migração para service discovery de cluster seria um item
novo, e valeria uma nova ADR (a regra 16 do repositório venceu o mecanismo automático do framework
uma vez — a decisão em 3.39.3 está registrada para o próximo bump).

**Regra de bump (como na ADR 009):** a cada bump de plataforma (= bump de `quarkus-smallrye-stork`),
re-verificar nos fontes do módulo os dois defectos do auto-registro (`health-check-url` relativa;
dereg após o CDI) e a exigência de `quarkus-jackson` com Consul, e rodar os quatro testes de
registro/descoberta (`*RegistersInConsulTest`, `RentalServiceDiscoveryTest`,
`ReservationsServiceDiscoveryTest`) — só se estiverem verdes e o mecanismo ainda defeituoso é que
esta ADR segue de pé; se um deles mudar, rever de ponta a ponta (regra 16).