# ADR 009 — Fault tolerance nas chamadas externas: timeout, retry seletivo e fallback que sinaliza

- **Status:** Accepted / implementada
- **Data:** 2026-10-05
- **Contexto:** Capítulo 10 — Cloud Native Patterns (fault tolerance)
- **Escopo:** `reservation-service`, nas duas fronteiras de saída: escrita para `rental-service` e leitura para `inventory-service`

## Contexto

O `reservation-service` é onde as duas chamadas síncronas de saída se encontram — uma escrita e uma leitura, com politicas de tolerancia a falha diferentes:

```text
reservation-service
   ├── POST /rentals          → rental-service      (escrita, em CreateReservation)
   └── GraphQL allCars        → inventory-service   (leitura, em FindAvailableVehicles)
```

Não é o único serviço do repositório com chamada síncrona de saída: o `users-service` também chama `reservation-service` (`GET /reservations/availability`, em `ReservationsRestGateway`), e hoje sem qualquer política — consequência registrada em "Consequências".

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

- O cliente entrega falha de I/O embrulhada em `CompletionException`, e as annotations casam pelo tipo. Por isso o gateway desembrulha a `CompletionException` antes de devolver a `Uni`: sem isso `IOException` nunca casaria e o retry seria letra morta de novo.
- Erro de GraphQL (`GraphQLClientException`) é **defeito do outro lado, não indisponibilidade**: não é repetido e não vira 503, sobe como erro inesperado. Falha de mapeamento fica fora das listas por omissão, pelo mesmo motivo — erro determinístico.

Limite conhecido desta taxonomia: `InvalidResponseException` não distingue 5xx de 4xx, porque o cliente não expõe o status como tipo. Uma URL de catálogo errada (404) é repetida 3 vezes antes de virar 503. Aceitamos isso em vez de fazer parse da mensagem de exceção, que seria mais frágil ainda.

O fallback **não devolve lista vazia**. Devolve o sinal `InventoryUnavailable` (`application/exception`), que é falha de aplicação, não valor de negócio. `FindAvailableVehiclesTest` fixa essa distinção: `[]` é um resultado legítimo; `InventoryUnavailable` é "não deu para saber".

`InventoryUnavailable` é uma classe final de aplicação, sem anotações de fault tolerance: as anotações de política ficam no adapter, e a fronteira HTTP é quem decide o formato.

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
quarkus.rest-client."...RentalClient".read-timeout = 1500   (aborta o request)
RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteDeadlineAboveTheTransportTimeout
```

O teste de guarda compara as duas com o valor exposto pela própria annotation, de modo que aumentar o deadline sem o ajuste correspondente do transporte quebra o build. O escopo dele é esse: **é uma guarda de configuração**, não a prova de que a requisição foi abortada — a prova do cancelamento a montante seria um teste de integração com o container fora do ar, que é evidência do item 9. É também por isso que a escrita **não tem override por perfil**: um deadline só, válido em todos, para que a relação com o prazo de transporte seja verificável em qualquer ambiente.

A leitura não tem essa segunda camada: o cliente GraphQL do SmallRye não expõe chave de prazo (verificado no modelo de configuração de `quarkus-smallrye-graphql-client` 3.39.3 — só `url`, proxy, TLS, WebSocket). Uma consulta de catálogo abandonada é inofensiva comparada com uma escrita, e o deadline da annotation cobre o caso: um socket travado estoura o `@Timeout`, que é `TimeoutException`, que está em `retryOn` e `applyOn`.

### 5. Política na annotation, valor operacional na config

O *o quê* (o que tem timeout, o que é repetido, o que vira indisponibilidade) é código e muda por decisão. O *quando* (2 s, 3 s, 10 ms de espera entre tentativas) é operacional e muda por ambiente, então vai para `application.properties`, no formato `quarkus.fault-tolerance."<classe>/<método>".<estratégia>.<atributo>`.

Armadilha registrada, porque custa caro em silêncio: **`timeout.value` sem `timeout.unit` herda a unidade da annotation** (`TimeoutConfigImpl.unit()` cai em `Timeout.unit()`, cujo padrão é milissegundos). Como a unidade da annotation é uma escolha nossa, o mesmo número muda de significado conforme ela:

```text
annotation @Timeout(unit = MILLIS) + timeout.value=300              → 300 ms
annotation @Timeout(unit = SECONDS) + timeout.value=300             → 300 s  (medido: 300002 ms)
timeout.value=300 + timeout.unit=MILLIS                            → 300 ms  (medido: 346 ms)
```

Por isso `timeout.unit` vem sempre explícito, e as classes de teste **medem o tempo decorrido** com limites bilaterais e `@Timeout` de classe — uma chave com nome errado ou sem unidade vira falha em segundos, não um teste que passa em silêncio nem uma suíte que demora minutos.

## Alternativas consideradas

**Retry na escrita.** Rejeitado: duplicaria locação. Uma chave de idempotência tornaria a escrita repetível, mas é mudança de contrato entre os dois serviços e está fora do escopo deste item. Quando existir, esta ADR pode ser revista: o teste de caracterização de `StartRental` é o que avisa.

**Fallback da leitura devolvendo lista vazia.** Rejeitado: é degradação silenciosa que produz uma resposta de negócio falsa. Um "vehicle catalog degradado" é melhor que um catálogo errado — e por isso a resposta é 503, não 200.

**Circuit breaker.** Adiado para o item 9. Faz sentido quando há taxa de erro observável e estado a compartilhar entre chamadas; aqui a política de timeout e retry já limita o estrago, e o breaker seria estado sem evidência.

**Bulkhead / limitador de concorrência.** Adiado para o item 9. Faz sentido para isolar Threads ou conexões sob carga, o que exige medição de concorrência entre as chamadas.

**Timeout manual com `Uni.ifNoItem().after(...)` no adapter.** Rejeitado como mecanismo único: duplicaria a política SmallRye em código e a tornaria menos auditável. O atrativo é que a versão Mutiny cancela de fato a subscription a montante — por isso o cancelamento foi medido e comparado, em vez de presumido.

**Retry manual dentro do adapter.** Rejeitado: mesma política fora do lugar padrão, mais difícil de revisar e de configurer por ambiente.

## Consequências

**Boas.**

- O cliente da disponibilidade recebe 503 e sabe que o resultado é inconclusivo; ele não "some" com a frota.
- Erro de GraphQL (inventory respondeu com `errors`) não é disfarçado de indisponibilidade nem de resultado de negócio.
- A escrita não duplica locação, mesmo com rental-service instável.
- Prazos por ambiente sem tocar em código de regra.

**Ruins.**

- Uma consulta de disponibilidade contra o inventory fora do ar custa 3 idas (1 tentativa + 2 repetições). Com inventory fora, o custo se multiplica por 3; é preciso observar taxa de erro e latência antes de escolher circuit breaker.
- Uma URL de catálogo errada (404) também é repetida 3 vezes antes de virar 503, porque o cliente não tipa o status.
- O `users-service`, que consome `GET /reservations/availability`, ainda não trata 503: precisa passar a tratar resultado inconclusivo em item próprio, em vez de receber lista vazia com o sentido errado. É dívida criada por esta ADR e visível agora.
- 503 é uma resposta nova para quem consome `GET /reservations/availability`; clientes precisam tratá-la explicitamente.
- A dívida de `CreateReservation` (reserva `PENDING` órfã quando a escrita falha) continua aberta e é visível agora, porque a falha deixa de ser engolida.

## Evidência

| Teste | O que fixa |
|---|---|
| `GraphQLInventoryClientFailureTest` (3 testes) | como o cliente real classifica cada falha: `CompletionException`→`IOException`, `InvalidResponseException`, `GraphQLClientException` — é a base medida da taxonomia |
| `RentalRestGatewayFaultToleranceTest.shouldTimeOutWhenRentalServiceNeverResponds` | deadline da escrita vale (≥1,5 s, <3 s) e propaga `TimeoutException`, com uma única chamada |
| `RentalRestGatewayFaultToleranceTest.shouldNotRetryRentalStartWhenItFails` | escrita não repete: exatamente uma chamada em falha |
| `RentalRestGatewayFaultToleranceTest.shouldKeepTheWriteDeadlineAboveTheTransportTimeout` | guarda de configuração: prazo de transporte menor que o deadline de FT |
| `RentalRestGatewayFaultToleranceTest.shouldStartTheRentalWhenTheServiceAnswersInTime` | caminho feliz não foi quebrado |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldReturnTheVehiclesWhenInventoryAnswersInTime` | resposta normal preservada |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldRetryWhenInventoryAnswersWithoutGraphqlEnvelope` | resposta HTTP sem envelope GraphQL é repetida e a leitura se recupera na 3ª tentativa |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldRetryWhenTheConnectionToInventoryFails` | `CompletionException` desembrulhada: falha de conexão é mesmo repetida (prova que o `IOException` casa) |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldFailWithInventoryUnavailableWhenTheReadKeepsTimingOut` | deadline estourado esgota as 3 tentativas e vira `InventoryUnavailable`, com tempo dentro da janela esperada |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldFailWithInventoryUnavailableWhenInventoryKeepsAnsweringWithoutGraphqlEnvelope` | esgotamento por resposta sem envelope também sinaliza indisponibilidade, com a causa preservada |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldNotRetryWhenInventoryAnswersWithGraphqlErrors` | erro de GraphQL não é repetido nem convertido em indisponibilidade |
| `FindAvailableVehiclesTest` | `[]` continua sendo resultado de negócio legítimo; `InventoryUnavailable` é sinal |
| `AvailabilityUnavailableTest` (3 testes) | 503 com `Retry-After` e corpo estável; 200 quando o inventory responde; sem vazar detalhe de infraestrutura |
| `AvailabilityThroughInventoryChainTest` (2 testes) | a cadeia inteira com o gateway real em CDI: 3 tentativas e depois 503; `200 []` quando o inventory responde vazio de verdade |
| `CreateReservationTest.shouldLeaveTheReservationPendingWhenTheRentalStartFails` | caracterização da dívida: escrita única, reserva fica `PENDING`, falha sobe |
| `StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice` | caracterização da não-idempotência que proíbe retry na escrita |

## Relação com outras decisões

- **ADR 002 (retry de mensagens)** — mesma conclusão em outro meio: retry só é seguro onde a operação é idempotente. Ali a garantia vem de `eventId`; aqui a leitura é idempotente por natureza e a escrita simplesmente não é repetível.
- **ADR 003 (DLQ)** — lá o destino da falha esgotada é uma fila morta. Aqui o destino é um sinal HTTP, porque a falha é de uma requisição síncrona que o cliente ainda está esperando.
- **ADR 008 (outbox)** — a dívida da escrita (reserva `PENDING` após falha) pede uma solução de saga/outbox para a transação entre contextos, que é o próximo tipo de decisão e não cabe aqui.

## Estado da decisão

**Accepted / implementada**, com evidência de teste unitário dos adapters, de caracterização do cliente real e de contrato da fronteira (inclusive a cadeia completa com o gateway em CDI). Faltam duas evidências, ambas do item 9: falha em integração real com o container fora do ar (que também provaria o abort da chamada em voo, hoje só guardado por configuração) e o tratamento do 503 pelo `users-service`.
