# ADR 009 — Fault tolerance nas chamadas externas: timeout, retry seletivo e fallback que sinaliza

- **Status:** Accepted / implementada
- **Data:** 2026-10-05 (revisada em 2026-10-21 — §6, o lado consumidor)
- **Contexto:** Capítulo 10 — Cloud Native Patterns (fault tolerance)
- **Escopo:** `reservation-service`, nas duas fronteiras de saída: escrita para `rental-service` e leitura para `inventory-service`; e `users-service`, na fronteira de consumo do `GET /reservations/availability` (§6)

## Contexto

O `reservation-service` é onde as duas chamadas síncronas de saída se encontram — uma escrita e uma leitura, com politicas de tolerancia a falha diferentes:

```text
reservation-service
   ├── POST /rentals          → rental-service      (escrita, em CreateReservation)
   └── GraphQL allCars        → inventory-service   (leitura, em FindAvailableVehicles)
```

Não é o único serviço do repositório com chamada síncrona de saída: o `users-service` também chama `reservation-service` (`GET /reservations/availability`, em `ReservationsRestGateway`). Ele é **consumidor de um contrato de falha** — e não produtor —, o que muda a política: a decisão está na §6.

Sem política explícita, as duas chamadas heredam o comportamento padrão do cliente HTTP: esperar até o prazo do transporte (no Quarkus, 30 s de leitura e 15 s de conexão por padrão). Isso consome orçamento da event loop, segura a requisição do cliente e, no caso da leitura, não dá ao consumidor como distinguir duas respostas semanticamente opostas:

```text
200 []                                 → "não há veículo disponível"  (verdade)
200 [] porque o inventory estava fora  → mentira
```

A segunda resposta é o problema que motiva a política: degradar para lista vazia transforma uma falha de infraestrutura em um fato de negócio falso, e o cliente age de forma errada — oferece outra data ao usuário como se o catálogo estivesse completo.

## Restrições que moldam a decisão

**A escrita não é idempotente.** `StartRental` (rental-service) sempre salva uma nova locação: não encontra locação existente para o mesmo par cliente/reserva e grava outra. Evidência executável em `StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice` — duas chamadas com o mesmo par cliente/reserva criam duas locações. Portanto repetir a escrita depois de um resultado incerto duplica locação.

**A leitura é idempotente e sem efeito colateral.** Repetir uma consulta GraphQL de catálogo é seguro; o custo é latência e carga.

**A escrita já tem uma dívida que esta ADR não cria.** `CreateReservation` salva a reserva antes de chamar o rental-service; uma falha nessa chamada deixa a reserva em `PENDING`. É comportamento pré-existente e fica registrado aqui como pendência, não corrigido neste item.

## Decisão

A política fica **nas annotations dos adapters de saída** (`adapter/out`), nunca nas portas nem no caso de uso: ela descreve a fronteira técnica, e as portas são contrato de negócio.

| Fronteira | Timeout | Retry | Fallback | Resposta ao cliente |
|---|---|---|---|---|
| Escrita `rental-service` | 2 s (um único valor, todos os perfis) | não | não | erro propagado como falha |
| Leitura `inventory-service` | 3 s em `%prod`, 300 ms em `%test` | até 2 repetições, só transitório | sinaliza indisponibilidade | 503 com corpo estável |

### 1. Escrita: somente timeout

`RentalRestGateway.start` tem apenas `@Timeout`.

- **Sem `@Retry`**: a operação não é idempotente (restrição acima). Um retry cego criaria locação duplicada para a mesma reserva.
- **Sem `@Fallback`**: um fallback que devolvesse sucesso confirmaria ao cliente que a locação começou, sem locação registrada. O fallback honesto para escrita é não ter fallback.

### 2. Leitura: timeout, retry seletivo e fallback que sinaliza

`GraphQLInventoryGateway.findVehicles` combina três anotações com listas de falha idênticas, para que "o que pode ser repetido" e "o que pode virar indisponibilidade" sejam a mesma decisão.

A lista **foi medida contra o cliente typesafe real** (`GraphQLInventoryClientFailureTest`, com um servidor HTTP de verdade), não deduzida da documentação do framework — a primeira versão desta ADR citava tipos JAX-RS (`ProcessingException`, `ServerErrorException`) que este cliente nunca lança, e portanto nunca repetia nem convertia nada:

```text
retryOn / applyOn = TimeoutException              deadline estourado
                 , InvalidResponseException      resposta HTTP sem envelope GraphQL
                 , IOException                   conexão recusada/resetada

fora das duas    = GraphQLClientException        inventory respondeu 200 com `errors`
```

Duas consequências dessa medição:

- Falha de I/O chega crua, como `IOException`: o Mutiny remove o embrulho de `CompletionStage` antes de emitir a falha, então `retryOn` casa direto e o gateway não desembrulha nada. Isso só é verificável observando a falha **por assinatura** — `await().indefinitely()` re-empacota exceção checada em `CompletionException`, e medir por ele atribuiria ao cliente uma forma de falha do próprio teste. Foi exatamente esse erro que a primeira versão deste item cometeu.
- Erro de GraphQL (`GraphQLClientException`) é **defeito do outro lado, não indisponibilidade**: não é repetido e não vira 503, sobe como erro inesperado. Falha de mapeamento fica fora das listas por omissão, pelo mesmo motivo — erro determinístico.

Limite conhecido desta taxonomia: `InvalidResponseException` não distingue 5xx de 4xx, porque o cliente não expõe o status como tipo. Uma URL de catálogo errada (404) é repetida 3 vezes antes de virar 503. Aceitamos isso em vez de fazer parse da mensagem de exceção, que seria mais frágil ainda.

O fallback **não devolve lista vazia**. Devolve o sinal `InventoryUnavailable` (`application/exception`), que é falha de aplicação, não valor de negócio. `FindAvailableVehiclesTest` fixa essa distinção: `[]` é um resultado legítimo; `InventoryUnavailable` é "não deu para saber".

`InventoryUnavailable` é uma classe final de aplicação, sem anotações de fault tolerance: as anotações de política ficam no adapter, e a fronteira HTTP é quem decide o formato. Para o caso de uso ela aparece só como `Uni` que falha — não existe camada de contrato intermediária.

O método de fallback recebe a causa como parâmetro extra (`inventoryUnreachable(Throwable)`), extensão do SmallRye FT disponível no modo não-compatível, que é o default do Quarkus. A MP FT só garante a forma sem argumento; com a extensão, a causa original chega ao log do adapter de saída em vez de virar um `Throwable` genérico.

### 3. A fronteira HTTP responde 503 com contrato estável

`InventoryUnavailableMapper` traduz o sinal em resposta:

- status `503` e header `Retry-After: 30`;
- corpo `{"code":"INVENTORY_UNAVAILABLE","message":"vehicle inventory is temporarily unavailable","retryAfterSeconds":30}`.

A causa original (host, porta, stack) fica no log, nunca no corpo da resposta. `AvailabilityUnavailableTest.shouldNotLeakInfrastructureDetailWhenInventoryIsUnavailable` fixa essa separação.

O `Retry-After: 30` é um valor único e deliberado: ele descreve o intervalo sugerido ao cliente HTTP que entende o header, não um dado operacional da plataforma. Se um dia precisar variar por ambiente, é uma chave de config nova, com o contrato respondendo pelo valor efetivo.

### 4. O `@Timeout` não cancela a chamada em voo — o prazo do transporte é que aborta

Este foi um achado medido, não presumido. O `@Timeout` do SmallRye Fault Tolerance em método que devolve `Uni` emite `TimeoutException` para o chamador, mas **não cancela a subscription a montante**: um emitter que só termina por cancelamento continuava vivo depois do deadline. Na escrita isso significa que a requisição HTTP de `POST /rentals` poderia ficar em voo muito depois de o deadline ter expirado, segurando conexão, com o padrão de 30 s do Quarkus.

Por isso a escrita tem duas camadas de prazo, e a segunda existe para compensar a primeira:

```text
RentalRestGateway.WRITE_DEADLINE_MILLIS = 2000   annotation @Timeout (orçamento da chamada)
quarkus.rest-client."...RentalClient".read-timeout = 1500   (limita connect e a espera por headers)
RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteDeadlineAboveTheTransportTimeout
```

O mecanismo merece precisão: `read-timeout` **não** é um deadline absoluto. Ele vira
`HttpClientRequest.setTimeout`, que o javadoc do Vert.x 4.5.33 descreve como `idleTimeout`
"com nome confuso" e marca como `@Deprecated`; a implementação **rearma** o tempo a cada chunk
recebido, **cancela** o timer quando chegam os headers, e o `idleTimeout` do socket fica no
default `0`, ou seja, desligado. O que ele de fato limita é a fase de connect (500 ms, via
`connect-timeout`, e o próprio `read-timeout` entra também como teto de aquisição de conexão do
pool) e a espera pelos headers — a leitura do corpo da resposta **não tem prazo**
no cliente REST do Quarkus.

A ordem continua correta e é o que importa para a decisão: mesmo quando o `read-timeout` não
estoura, o `@Timeout` de 2 s entrega `TimeoutException` ao chamador — o que ele não faz é
abortar a chamada em voo.

A prova possível do abort nunca foi o cliente mockado (ele não tem socket para abortar): é o
outro lado real — um peer TCP de verdade, em que o socket existe, o pedido sai por ele e o
fechamento é observado pela outra ponta — e, precisamente, um outro lado que **aceita** a
conexão e não responde. Um container realmente fora do ar não serviria: ele recusa a conexão,
e recusa não tem chamada em voo para abortar.

O peer roda na JVM do teste: o que a prova exige não é isolamento de processo do peer, e sim
que o socket do cliente exista de verdade e que a outra ponta veja o fechamento. Por isso esta
evidência é **de JVM** — o peer em processo separado (o desenho que o caminho nativo exigiria)
não é o que está medido aqui.

Essa prova existe, e é um par de testes sobre o caminho de produção inteiro. Um Consul real
publica a instância "rentals" apontando para um **buraco negro** — um `ServerSocket` cru que
aceita a conexão, lê o pedido e nunca responde. O caminho exercitado é o de produção inteiro: o
`RentalClient` de produção (`stork://rentals`), resolvido pelo Stork no Consul, e o
`RentalGateway` de produção, com o `@Timeout` de 2 s.

O buraco negro é socket cru, e não `HttpServer`, por uma razão medida: o abort chega como
**fim de stream**, e não como reset — o fechamento é limpo, sem RST. Então o EOF na próxima
leitura é o sinal que distingue "o cliente abortou" de "o cliente desistiu por timeout e deixou
a conexão pendurada". É a diferença entre abortar e desistir, e do lado do cliente ela é
invisível. O que o harness mede é exatamente esse EOF, e é o EOF que a asserção exige.

O lado **positivo** (`RentalTransportAbortTest`) tem o prazo de transporte de produção (1500 ms),
abaixo do deadline de FT (2000 ms). Medido:

```text
elapsed = 1545 ms
CompletionException → io.vertx.core.impl.NoStackTraceTimeoutException:
  "The timeout period of 1500ms has been exceeded while executing POST /rental/start/alice/42 for server <endereço>"
peer: o cliente FECHOU a conexão (fim de stream lido pelo servidor)
```

Os 1545 ms são o que separa o transporte do `@Timeout`: o deadline de FT (2 s) ainda não tinha
vencido quando a falha chegou, e a causa raiz é um `java.util.concurrent.TimeoutException` — a
hierarquia do Vert.x, disjunta da do SmallRye FT, que deriva de `FaultToleranceException`. O
teste afirma também que o peer recebeu o pedido de produção (`POST /rental/start/alice/42`) e
que o fechamento foi limpo, e não um reset.

O lado **negativo** (`RentalTransportTimeoutAboveFaultToleranceDeadlineTest`) é o contraditório do
mesmo experimento: o mesmo caminho de produção, o mesmo buraco negro, e **uma** variável trocada
— o prazo de transporte acima do deadline de FT (5000 ms, por um `@TestProfile`). A ordem se
inverte, e o resultado é o oposto: o deadline de FT vence aos ~2.018 ms e entrega
`TimeoutException` ao chamador, o prazo de transporte não dispara, e **o socket continua
aberto** do lado do peer. A chamada foi abandonada, não abortada — é a caracterização do
`@Timeout` que não cancela, agora observada no fio real e não no mock.

Sem o lado negativo, o positivo provaria apenas que a chamada terminou: o deadline de FT também
termina a chamada, e nada no lado do cliente distingue as duas coisas.

E a mutação está **versionada**, não em um experimento manual: ela é um perfil de teste
(`TransportTimeoutAboveDeadlineProfile`), então o build verifica a cada execução que o
comportamento do caminho de produção depende mesmo do prazo — e o gêmeo falha alto se a mutação
deixar de ser aplicada.

**Qual fase está provada.** O `read-timeout` vira `HttpClientRequest.setTimeout`, que é
inatividade e **se cancela quando chegam os headers** (§2). A prova acima cobre a fase que ele
de fato limita: a espera pelos headers, que no buraco negro nunca chegam. A leitura do corpo
**depois** dos headers continua sem prazo — a limitação que esta ADR já registra no §2 —, e
esta revisão não a cobre nem a promete. É a mesma ressalva que vale para o cliente GraphQL
(logo abaixo).

O teste de guarda compara as duas com o valor exposto pela própria annotation, de modo que aumentar o deadline sem o ajuste correspondente do transporte quebra o build. O escopo dele continua sendo esse: **é uma guarda de configuração** — quem prova que a requisição foi abortada é o par de testes com o peer real. É também por isso que a escrita **não tem override por perfil**: um deadline só, válido em todos, para que a relação com o prazo de transporte seja verificável em qualquer ambiente. (O perfil que existe — `TransportTimeoutAboveDeadlineProfile` — é de **teste**, e existe para provar o contrário.)

A leitura não tem essa segunda camada: o cliente GraphQL do SmallRye não expõe chave de prazo para o HTTP (verificado no modelo de configuração de `quarkus-smallrye-graphql-client` 3.39.3 — `url`, headers, proxy, TLS, `max-redirects` e `init-payload`; o único timeout é `websocket-initialization-timeout`, do canal WebSocket). Uma consulta de catálogo abandonada custa menos que uma escrita, porque é idempotente e o fallback existe. O deadline da annotation cobre o chamador, mas **não** a conexão: como o `@Timeout` não cancela, a requisição abandonada persiste nos defaults do Vert.x 4.5.33 medidos nesta revisão — `connect-timeout` 60 s e `idleTimeout` 0, isto é, **sem prazo de leitura**. São até 60 s de conexão retida na fase de connect, ou indefinidamente na de read, por tentativa, ×3. A configuração não oferece uma segunda camada como no REST client, então esse custo é aceito como dívida conhecida, e não como ausência de risco.

O casamento por tipo considera a cadeia de causas, não só a exceção de topo: é o modo
não-compatível (`SpecCompatibility.inspectExceptionCauseChain`) que o habilita. Hoje isso não
alarga nada, porque `GraphQLClientException` 2.18.5 só tem construtores `(String, GraphQLError)`
e `(String, List<GraphQLError>)`, sem causa — e é
`AvailabilityThroughInventoryChainTest.shouldNotDisguiseGraphqlErrorsAsInventoryUnavailable` que
fixa essa invariante. Um bump do cliente que passe a encadear causa transformaria "erro de
GraphQL" em 503 silenciosamente.

### 5. Política na annotation, valor operacional na config

O *o quê* (o que tem timeout, o que é repetido, o que vira indisponibilidade) é código e muda por decisão. O *quando* (2 s, 3 s, 10 ms de espera entre tentativas) é operacional e muda por ambiente, então vai para `application.properties`, no formato `quarkus.fault-tolerance."<classe>/<método>".<estratégia>.<atributo>`.

Armadilha registrada, porque custa caro em silêncio: **`timeout.value` sem `timeout.unit` herda a unidade da annotation** (`TimeoutConfigImpl.unit()` cai em `Timeout.unit()`, cujo padrão é milissegundos). Como a unidade da annotation é uma escolha nossa, o mesmo número muda de significado conforme ela:

```text
annotation @Timeout(unit = MILLIS) + timeout.value=300              → 300 ms
annotation @Timeout(unit = SECONDS) + timeout.value=300             → 300 s  (medido: 300002 ms)
timeout.value=300 + timeout.unit=MILLIS                            → 300 ms  (medido: 346 ms)
```

Por isso `timeout.unit` vem sempre explícito, e as classes de teste **medem o tempo decorrido** com limites bilaterais e `@Timeout` de classe — uma chave com nome errado ou sem unidade vira falha em segundos, não um teste que passa em silêncio nem uma suíte que demora minutos.

### 6. O consumidor (`users-service`): prazo de transporte e sinal de aplicação, sem repetir o que já foi repetido

O `users-service` chama `GET /reservations/availability` com um cliente **bloqueante** (`AvailabilityClient.availability` devolve `Collection<Car>`). Até este item a chamada ia sem prazo e sem tratamento: o cliente herdava os 30 s de leitura do Quarkus e qualquer falha do outro lado virava exceção crua para a tela. Com o retry de leitura do §2, o caminho de falha passou a custar ~9 s em `%prod` (3 idas × 3 s de deadline do reservation) segurando thread do BFF — e, quando o reservation respondia 503, o BFF não traduzia nada.

A política do consumidor é **propositalmente diferente** da do produtor, porque os dois papéis são diferentes:

```text
connect-timeout = 500      fase de conexão, curta e sem valor semântico
read-timeout    = 3000     a espera pelo outro lado; estourar = mesmo resultado do 503
```

- **Sem `quarkus-smallrye-fault-tolerance`.** O upstream já tem timeout, retry seletivo e fallback que sinaliza (§2) e **já devolve 503 com corpo estável** (§3). Repetir no BFF não produziria um sinal novo — só multiplicaria a carga e a espera sobre um serviço já em falha, com o retry interno dele contando 3×. O BFF não é o lugar certo para repetir; é o lugar para **traduzir**.
- **O prazo vai para a config, no formato do REST client** (`quarkus.rest-client.reservations-availability.connect-timeout=500`, `read-timeout=3000`), não para annotations: a limitação é do transporte, que é onde a chamada bloqueante retém a thread. O `read-timeout` tem a mesma natureza medida documentada no §4 — inatividade com rearmamento, não deadline absoluto —, então ele cobre connect e a espera por headers, não a leitura do corpo.
- **A configKey é própria da leitura, por causa da escrita.** `quarkus.rest-client.<configKey>.*` é configuração **por cliente**, não por método — e o cliente que existia antes carregava os três caminhos (`all`, `create`, `availability`) sob a mesma `reservations`. O efeito colateral teria sido aplicar o prazo de 3 s da leitura ao `POST /reservations`, que é **não idempotente** (restrição medida em `StartRentalTest`, o eixo inteiro das §2/§3): um prazo curto numa escrita não idempotente a transforma em escrita de resultado incerto, que é exatamente o modo que esta ADR proíbe repetir. A separação é o próprio motivo de existir `AvailabilityClient`, e a guarda é executável: `ReservationsRestGatewayTest.shouldNotPutTheAvailabilityDeadlineOnTheNonIdempotentWrite` falha se os prazos voltarem para a configKey da escrita.
- **A taxonomia é medida, não deduzida — e fixada.** `ReservationsClientFailureTest` faz a caracterização contra um servidor HTTP de verdade: o 503 do outro lado chega como `ClientWebApplicationException` (um `WebApplicationException`), e conexão recusada e estouro de prazo chegam como `ProcessingException`. Só esses dois tipos entram no `catch` — união de padrão, não um `catch (Exception)`: a lista é curta e medida. Cada caso **verifica o tipo** (`assertInstanceOf`) além de imprimir a medida: sem isso a classe ficaria verde se um upgrade do Quarkus mudasse a taxonomia, e o `catch` do adapter viraria letra morta com a suíte passando — o mesmo defeito que motivou esta ADR, do outro lado.
- **O resultado inconclusivo vira sinal de aplicação, nunca lista vazia.** O adapter de saída lança `AvailabilityUnavailable` (`application/exception`) — o análogo do `InventoryUnavailable`, sem anotações de política. `ReservationFacadeTest.shouldPropagateAvailabilityUnavailableInsteadOfReturningAnEmptyList` fixa a distinção: `[]` é "nenhum veículo no período"; o sinal é "não deu para saber". O adapter inbound (`AvailabilityUnavailableMapper`) responde `503` com `Retry-After: 30` e o fragmento explicativo. A causa vai para `LOG.warnf` **no mesmo `catch`** (mesmo nível do `GraphQLInventoryGateway.inventoryUnreachable`) e nunca para o corpo — é obrigatório, não decorativo: o mapper consome a exceção via `@ServerExceptionMapper`, então o Quarkus não a loga sozinho, e sem essas linhas o mascaramento da escolha 2 abaixo ficaria sem canal de diagnóstico. `ReservationsAvailabilityUnavailableTest.shouldNotLeakInfrastructureDetailWhenAvailabilityIsUnavailable` nega o vazamento no corpo.

Duas escolhas desta fronteira são deliberadas e ficam explícitas aqui, porque são as que os guardians tendem a questionar:

1. **O BFF não instala a extensão de FT.** Isso não é omissão: a extensão (e a tabela de dependências do cap. 10) permanece com o `reservation-service` como único consumidor dela. Se um dia o BFF precisar de repetição ou de bulkhead, aí a extensão entra — mas aí haveria um medidor que a justificasse.
2. **Qualquer falha upstream vira 503 do BFF, não só o 503 do reservation.** Um 500 do reservation, um 401 de token propagado, um 404 de path mudado: o browser recebe "temporarily unavailable, try again". A escolha protege o usuário e o contrato (`contracts.md`) de vazar detalhe de infraestrutura em erro de rede; o custo é mascarar defeito determinístico do upstream como indisponibilidade transitória. É o mesmo tipo de trade-off assumido no §3 com a URL de catálogo errada, e pela mesma razão: não fazer `if` sobre mensagem de exceção ou sobre status cru seria mais frágil que a própria mascarada.

O fragmento da indisponibilidade é `text/html`, porque o consumidor é o htmx: o `index.html` registra um `htmx:beforeSwap` que força a troca das respostas 503 — sem ele o htmx descarta o corpo de erro e a tabela simplesmente não muda. O gancho é **escopado ao alvo** (`event.detail.target.id === 'availability'`), para que um 503 de `/get` ou `/reserve` continue sendo erro e não seja trocado em silêncio. É o único elo da cadeia 503→usuário sem evidência executável: o repo não tem infra de JS, então o hook é **risco residual conhecido** — se algum dia o htmx mudar a forma do `beforeSwap`, quem o descobrirá é o usuário, não a suíte.

## Alternativas consideradas

**Retry na escrita.** Rejeitado: duplicaria locação. Uma chave de idempotência tornaria a escrita repetível, mas é mudança de contrato entre os dois serviços e está fora do escopo deste item. Quando existir, esta ADR pode ser revista: o teste de caracterização de `StartRental` é o que avisa.

**Fallback da leitura devolvendo lista vazia.** Rejeitado: é degradação silenciosa que produz uma resposta de negócio falsa. Um "vehicle catalog degradado" é melhor que um catálogo errado — e por isso a resposta é 503, não 200.

**Circuit breaker.** Adiado até o reservation-service expor `/q/metrics` com consumidor. Faz sentido quando há taxa de erro observável e estado a compartilhar entre chamadas; aqui a política de timeout e retry já limita o estrago, e o breaker seria estado sem evidência.

**Bulkhead / limitador de concorrência.** Adiado até o reservation-service expor `/q/metrics` com consumidor. Faz sentido para isolar Threads ou conexões sob carga, o que exige medição de concorrência entre as chamadas.

**Timeout manual com `Uni.ifNoItem().after(...)` no adapter.** Rejeitado como mecanismo único: duplicaria a política SmallRye em código e a tornaria menos auditável. O atrativo é que a versão Mutiny cancela de fato a subscription a montante — por isso o cancelamento foi medido e comparado, em vez de presumido.

**Retry manual dentro do adapter.** Rejeitado: mesma política fora do lugar padrão, mais difícil de revisar e de configurer por ambiente.

**Repetir a política no `users-service` (§6).** Rejeitado: o BFF consome um contrato de falha que **já** existe e **já** cumpre a repetição do outro lado. Um `@Retry` no BFF transformaria 1 ida doente em 3 idas doentes (3 × o `read-timeout` de 3 s do BFF), sobre um serviço já em falha, sem acrescentar um sinal novo — a resposta voltaria 503 de qualquer forma. O que o BFF precisa não é repetir, é **não esperar para sempre** (prazos de transporte) e **não mentir** (sinal + fragmento). Se um dia o BFF precisar de repetição, a condição é objetiva e mensurável: taxa de erro observável em `/q/metrics` do reservation, e aí entra junto o breaker da alternativa acima.

## Consequências

**Boas.**

- O cliente da disponibilidade recebe 503 e sabe que o resultado é inconclusivo; ele não "some" com a frota.
- Erro de GraphQL (inventory respondeu com `errors`) não é disfarçado de indisponibilidade nem de resultado de negócio.
- A escrita não duplica locação, mesmo com rental-service instável.
- Prazos por ambiente sem tocar em código de regra.
- O BFF herda o mesmo sinal: o browser recebe 503 com explicação em vez de tabela vazia, e a thread do BFF tem prazo para sair da chamada de falha.

**Ruins.**

- Uma consulta de disponibilidade contra o inventory fora do ar custa 3 idas (1 tentativa + 2 repetições). Com inventory fora, o custo se multiplica por 3; é preciso observar taxa de erro e latência antes de escolher circuit breaker.
- Uma URL de catálogo errada (404) também é repetida 3 vezes antes de virar 503, porque o cliente não tipa o status.
- ~~O `users-service`, que consome `GET /reservations/availability`, ainda não trata o 503~~ — **resolvido nesta revisão** (§6). O BFF agora tem prazo de transporte (`connect-timeout=500`, `read-timeout=3000`), converte a falha medida em `AvailabilityUnavailable` e responde 503 com o fragmento explicativo. O caminho de falha deixou de ser ilimitado e o resultado inconclusivo deixou de virar lista vazia. O que permanece é uma consequência nova: **503 virou resposta do `GET /available` do BFF**, então o browser/htmx precisa tratá-la — e trata, via `htmx:beforeSwap`.
- 503 é uma resposta nova para quem consome `GET /reservations/availability`; o BFF já a trata, mas **outros** consumidores do endpoint precisam tratá-la explicitamente.
- O 503 do BFF cobre **qualquer** falha upstream (§6): um 500 determinístico do reservation aparece para o browser como "tente de novo em instantes". É mascarada deliberada, com a causa no log do adapter de saída.
- A espera máxima do BFF por uma disponibilidade caiu de 30 s (default do Quarkus) para ~3,5 s (500 ms de connect + 3 s de read), mas `read-timeout` é inatividade com rearmamento: um fluxo de bytes que nunca para não estoura. O **abort** da chamada em voo está provado no caminho de escrita (§4, o par `RentalTransportAbortTest` + `RentalTransportTimeoutAboveFaultToleranceDeadlineTest`, peer real) — é o mesmo mecanismo do REST client (`read-timeout` → `HttpClientRequest.setTimeout`, o idle timeout **da requisição**; o `idleTimeout` do socket segue desligado, §2 → fechamento da conexão), e vale para a fase que ele de fato limita, a espera pelos headers. O que não foi repetido é a prova no cliente de leitura do BFF: lá não há `@Timeout`, logo não há dois prazos a distinguir, e a leitura é idempotente com fallback.
- A dívida de `CreateReservation` (reserva `PENDING` órfã quando a escrita falha) continua aberta e é visível agora, porque a falha deixa de ser engolida.
- O erro de GraphQL sai como **500 sem corpo estável**: o `@ServerExceptionMapper` cobre só `InventoryUnavailable`, então esse caminho não tem contrato de corpo. Um mapper de erro inesperado é contrato novo (roadmap.md, cap. 10). Hoje o mesmo recurso tem dois regimes de erro assimétricos — `/availability` tem 503 estável e `/reservations` tem 500 cru.
- A assinatura do fallback `inventoryUnreachable(Throwable)` só resolve no modo não-compat, que esta ADR trata como default. A propriedade real é `quarkus.fault-tolerance.mp-compatibility` (booleana, default `false` no Quarkus) — não existe `compatibility-mode`. Se alguém declarar `mp-compatibility=true`, o fallback deixa de casar e o **build falha em augmentação** (`FaultToleranceDefinitionException`: "can't find fallback method … with matching parameter types and return type"), que é bem melhor do que degradar em silêncio. O teste que exige a causa original preservada é também a prova desse modo.
- A taxonomia de `retryOn` vale para `quarkus-smallrye-graphql-client` **3.39.3 medido**: um bump de `quarkus.platform.version` pode trocar `InvalidResponseException` por outro tipo e transformar retry/fallback em letra morta, em silêncio, porque o teste injeta os tipos à mão. É a razão de `GraphQLInventoryClientFailureTest` existir como caracterização: rodá-lo a cada bump de plataforma.

## Evidência

| Teste | O que fixa |
|---|---|
| `GraphQLInventoryClientFailureTest` (3 testes) | como o cliente real classifica cada falha, observada **por assinatura**: conexão recusada → `IOException` crua (sem `CompletionException`), resposta HTTP sem envelope → `InvalidResponseException`, 200 com `errors` → `GraphQLClientException` — é a base medida da taxonomia |
| `RentalRestGatewayFaultToleranceTest.shouldTimeOutWhenRentalServiceNeverResponds` | deadline da escrita vale (≥1,5 s, <3 s) e propaga `TimeoutException`, com uma única chamada |
| `RentalRestGatewayFaultToleranceTest.shouldNotRetryRentalStartWhenItFails` | escrita não repete: exatamente uma chamada em falha |
| `RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteCallInFlightWhenTheFaultToleranceDeadlineFires` | caracterização da biblioteca: o `@Timeout` avisa o chamador e **não** cancela a chamada a montante (é a premissa da segunda camada de prazo) |
| `RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteDeadlineAboveTheTransportTimeout` | guarda de configuração: `connect-timeout` e `read-timeout` abaixo do deadline de FT (as duas chaves, não só uma) |
| `RentalTransportAbortTest.shouldAbortTheWriteInFlightWhenTheTransportTimeoutFires` | **o abort, provado com peer real**: caminho de produção (Consul real → Stork → `RentalClient` → `RentalGateway` com `@Timeout`), a falha chega em ~1,5 s — antes do deadline de FT de 2 s — com causa `java.util.concurrent.TimeoutException` (a hierarquia do Vert.x, disjunta da do SmallRye FT, observada **por tipo**, sem parse de mensagem), e o buraco negro do outro lado viu a conexão fechar **por fim de stream**, e não por reset. O teste também afirma que o peer recebeu o pedido de produção (`POST /rental/start/alice/42`) |
| `RentalTransportTimeoutAboveFaultToleranceDeadlineTest.shouldAbandonTheWriteInFlightWhenTheTransportTimeoutIsAboveTheFaultToleranceDeadline` | contraditório do anterior, com **uma** variável trocada (prazo de transporte acima do deadline de FT, por `@TestProfile`): mesmo caminho de produção e mesmo peer → o deadline de FT vence aos ~2 s, o prazo de transporte não dispara e **o socket continua aberto** (a chamada foi abandonada, não abortada). Sem ele, o teste anterior provaria que a chamada terminou, não que foi abortada — e a mutação fica **versionada**, então o build verifica a força da prova a cada execução |
| `RentalRestGatewayFaultToleranceTest.shouldStartTheRentalWhenTheServiceAnswersInTime` | caminho feliz não foi quebrado |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldReturnTheVehiclesWhenInventoryAnswersInTime` | resposta normal preservada |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldRetryWhenInventoryAnswersWithoutGraphqlEnvelope` | resposta HTTP sem envelope GraphQL é repetida e a leitura se recupera na 3ª tentativa |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldRetryWhenTheConnectionToInventoryFails` | `IOException` (no teste, `ConnectException`) é repetida: é a forma que o cliente real emite |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldFailWithInventoryUnavailableWhenTheReadKeepsTimingOut` | deadline estourado esgota as 3 tentativas e vira `InventoryUnavailable`, com tempo dentro da janela esperada e sem cancelar a chamada a montante |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldFailWithInventoryUnavailableWhenInventoryKeepsAnsweringWithoutGraphqlEnvelope` | esgotamento por resposta sem envelope também sinaliza indisponibilidade, com a causa preservada |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldNotRetryWhenInventoryAnswersWithGraphqlErrors` | erro de GraphQL não é repetido nem convertido em indisponibilidade |
| `FindAvailableVehiclesTest` | `[]` continua sendo resultado de negócio legítimo; `InventoryUnavailable` é sinal |
| `AvailabilityUnavailableTest` (3 testes) | 503 com `Retry-After` e corpo estável; 200 quando o inventory responde; sem vazar detalhe de infraestrutura |
| `AvailabilityThroughInventoryChainTest` (3 testes) | a cadeia inteira com o gateway real em CDI: 3 tentativas e depois 503; `200 []` quando o inventory responde vazio de verdade; erro de GraphQL sai como 500, não disfarçado de indisponibilidade |
| `ReservationWriteFailureTest` (3 testes) | contrato de falha da escrita: prazo estourado e chamada recusada dão 500 com **uma** ida ao rental-service, e a reserva fica visível como `PENDING` |
| `CreateReservationTest.shouldLeaveTheReservationPendingWhenTheRentalStartFails` | caracterização da dívida: escrita única, reserva fica `PENDING`, falha sobe |
| `StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice` | caracterização da não-idempotência que proíbe retry na escrita |
| `ReservationsClientFailureTest` (3 testes, users-service) | taxonomia **medida e fixada** do cliente REST do BFF: 503 → `ClientWebApplicationException` (um `WebApplicationException`), conexão recusada → `ProcessingException`, estouro de prazo → `ProcessingException` (causa `TimeoutException`) — cada caso faz `assertInstanceOf`, então um upgrade que mude a taxonomia fica vermelho; é a base do `catch` do adapter de saída |
| `ReservationsRestGatewayTest.shouldSignalAvailabilityUnavailableWhenReservationsAnswersAnError` | resposta não-2xx do reservation vira `AvailabilityUnavailable`, não lista vazia |
| `ReservationsRestGatewayTest.shouldSignalAvailabilityUnavailableWhenTheTransportFails` | falha de transporte também vira `AvailabilityUnavailable` |
| `ReservationsRestGatewayTest.shouldMapTheFleetWhenReservationsResponds` | caminho feliz preservado |
| `ReservationsRestGatewayTest.shouldBoundTheAvailabilityReadWithTransportTimeouts` | guarda de configuração: as duas chaves do cliente de leitura existem e são positivas e curtas (≤1 s connect, ≤3 s read) |
| `ReservationsRestGatewayTest.shouldNotPutTheAvailabilityDeadlineOnTheNonIdempotentWrite` | guarda contra o acoplamento: `quarkus.rest-client.reservations.read-timeout` (a configKey da escrita não idempotente) segue o default do Quarkus — falha se os prazos voltarem para serem globais |
| `ReservationFacadeTest.shouldPropagateAvailabilityUnavailableInsteadOfReturningAnEmptyList` | a fachada **propaga** o sinal: `[]` é resultado legítimo e o sinal é "não deu para saber" |
| `ReservationsAvailabilityUnavailableTest` (3 testes) | contrato HTTP do BFF: 503 com `Retry-After: 30` e o fragmento explicativo; 200 quando o reservation responde; nenhum detalhe de infraestrutura no corpo |

## Relação com outras decisões

- **ADR 002 (retry de mensagens)** — mesma conclusão em outro meio: retry só é seguro onde a operação é idempotente. Ali a garantia vem de `eventId`; aqui a leitura é idempotente por natureza e a escrita simplesmente não é repetível.
- **ADR 003 (DLQ)** — lá o destino da falha esgotada é uma fila morta. Aqui o destino é um sinal HTTP, porque a falha é de uma requisição síncrona que o cliente ainda está esperando.
- **ADR 008 (outbox)** — a dívida da escrita (reserva `PENDING` após falha) pede uma solução de saga/outbox para a transação entre contextos, que é o próximo tipo de decisão e não cabe aqui.

## Estado da decisão

**Accepted / implementada**, com evidência de teste unitário dos adapters, de caracterização do cliente real (os dois lados: `GraphQLInventoryClientFailureTest` no reservation e `ReservationsClientFailureTest` no BFF) e de contrato da fronteira (inclusive a cadeia completa com o gateway de leitura em CDI e o contrato HTTP do `GET /available` do BFF). O **abort** da chamada em voo, que era a prova que faltava, está fechado desde 2026-10-22: um **par** de testes (`RentalTransportAbortTest` e `RentalTransportTimeoutAboveFaultToleranceDeadlineTest`) exercita o caminho de produção contra um peer real (Consul real → Stork → buraco negro) e observa, do outro lado do socket, o instante em que a conexão é fechada — com o contraditório que prova que o fechamento é causado pelo prazo de transporte e não pelo deadline de FT, e com a mutação **versionada** (`TransportTimeoutAboveDeadlineProfile`), de modo que o build verifica a força da prova a cada execução. O escopo é a fase que o prazo de transporte de fato limita — a espera pelos headers da resposta (§4): a leitura do corpo depois dos headers continua sem prazo, como já registrado no §2. A prova depende do backend **Consul** (`@Tag("consul")`): com o perfil `-P kubernetes`, o par não roda, porque o caminho de descoberta testado é o de Consul. Falta **uma** evidência, ainda aberta no roadmap do cap. 10: um mapper de erro inesperado no reservation para o `POST /reservations` deixar de sair como 500 cru.
