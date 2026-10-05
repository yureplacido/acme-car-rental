# ADR 009 — Fault tolerance nas chamadas externas: timeout, retry seletivo e fallback que sinaliza

- **Status:** Accepted / implementada
- **Data:** 2026-10-05
- **Contexto:** Capítulo 10 — Cloud Native Patterns (fault tolerance)
- **Escopo:** `reservation-service`, nas duas fronteiras de saída: escrita para `rental-service` e leitura para `inventory-service`

## Contexto

O `reservation-service` é o único serviço com chamadas síncronas de saída para outros serviços:

```text
reservation-service
   ├── POST /rentals          → rental-service      (escrita, em CreateReservation)
   └── GraphQL allCars        → inventory-service   (leitura, em FindAvailableVehicles)
```

Sem política explícita, as duas chamadas heredam o comportamento padrão do cliente HTTP: esperar até o prazo do transporte (no Quarkus, 30 s de leitura e 15 s de conexão por padrão). Isso consome orçamento da event loop, segura a requisição do cliente e, no caso da leitura, não dá ao consumidor como distinguir duas respostas semanticamente opostas:

```text
200 []                                 → "não há veículo disponível"  (verdade)
200 [] porque o inventory estava fora  → mentira
```

A segunda resposta é o problema que motiva a política: degradar para lista vazia transforma uma falha de infraestrutura em um fato de negócio falso, e o cliente age de forma errada — oferece outra data ao usuário como se o catálogo estivesse completo.

## Restrições que moldam a decisão

**A escrita não é idempotente.** `StartRental` (rental-service) sempre salva uma nova locação e nunca consulta `findByCustomerAndReservation` antes de gravar. Evidência executável em `StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice`: duas chamadas com o mesmo par cliente/reserva criam duas locações, e a segunda consulta do repositório nunca chega a acontecer. Portanto repetir a escrita depois de um resultado incerto duplica locação.

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

`GraphQLInventoryGateway.findVehicles` combina três anotações com listas de falha idênticas, para que "o que pode ser repetido" e "o que pode virar indisponibilidade" sejam a mesma decisão:

```text
retryOn / applyOn = TimeoutException        deadline estourado
                 , ProcessingException     falha de I/O do client
                 , ServerErrorException    resposta 5xx do inventory

abortOn           = ClientErrorException   4xx: consulta invalida, repetir não muda nada
                                            e não é indisponibilidade
```

Falha de mapeamento (`MappingException`) fica fora das duas listas por omissão: erro determinístico, não transitório.

O fallback **não devolve lista vazia**. Devolve o sinal `InventoryUnavailable` (`application/exception`), que é falha de aplicação, não valor de negócio. `FindAvailableVehiclesTest` fixa essa distinção: `[]` é um resultado legítimo; `InventoryUnavailable` é "não deu para saber".

`InventoryUnavailable` é uma classe final de aplicação, sem anotações de fault tolerance: as anotações de política ficam no adapter, e a fronteira HTTP é quem decide o formato.

### 3. A fronteira HTTP responde 503 com contrato estável

`InventoryUnavailableMapper` traduz o sinal em resposta:

- status `503` e header `Retry-After: 30`;
- corpo `{"code":"INVENTORY_UNAVAILABLE","message":"vehicle inventory is temporarily unavailable","retryAfterSeconds":30}`.

A causa original (host, porta, stack) fica no log, nunca no corpo da resposta. `AvailabilityUnavailableTest.shouldNotLeakInfrastructureDetailWhenInventoryKeepsFailing` fixa essa separação.

### 4. O `@Timeout` não cancela a chamada em voo — o prazo do transporte é que aborta

Este foi um achado medido, não presumido. O `@Timeout` do SmallRye Fault Tolerance em método que devolve `Uni` emite `TimeoutException` para o chamador, mas **não cancela a subscription a montante**: um emitter que só termina por cancelamento continuava vivo depois do deadline. Na escrita isso significa que a requisição HTTP de `POST /rentals` poderia ficar em voo muito depois de o deadline ter expirado, segurando conexão, com o padrão de 30 s do Quarkus.

Por isso a escrita tem duas camadas de prazo, e a segunda existe para compensar a primeira:

```text
RentalRestGateway.WRITE_DEADLINE_MILLIS = 2000   annotation @Timeout (orçamento da chamada)
quarkus.rest-client."...RentalClient".read-timeout = 1500   (aborta o request)
RentalRestGatewayFaultToleranceTest.shouldAbortTheInFlightWriteBeforeTheFaultToleranceDeadline
```

O teste de guarda compara as duas com o valor exposto pela própria annotation, de modo que aumentar o deadline sem o ajuste correspondente do transporte quebra o build. É também por isso que a escrita **não tem override por perfil**: um deadline só, válido em todos, para que a relação com o prazo de transporte seja verificável em qualquer ambiente.

A leitura não tem equivalente: o cliente GraphQL do SmallRye não expõe chave de prazo (verificado no modelo de configuração de `quarkus-smallrye-graphql-client` 3.39.3 — só `url`, proxy, TLS, WebSocket), e uma consulta de catálogo abandonada é inofensiva comparada com uma escrita.

### 5. Política na annotation, valor operacional na config

O *o quê* (o que tem timeout, o que é repetido, o que vira indisponibilidade) é código e muda por decisão. O *quando* (2 s, 3 s, 10 ms de espera entre tentativas) é operacional e muda por ambiente, então vai para `application.properties`, no formato `quarkus.fault-tolerance."<classe>/<método>".<estratégia>.<atributo>`.

Armadilha registrada, porque custa caro em silêncio: **o valor sem unidade na config é lido em segundos, e o `unit` padrão da annotation `@Timeout` é milissegundos**. `timeout.value=300` na config valeria 5 minutos. Por isso `timeout.unit` vem sempre explícito, e as classes de teste **medem o tempo decorrido** — uma chave com nome errado ou sem unidade quebra a asserção em vez de passar em silêncio.

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
- Falha determinística do inventory (consulta inválida, 4xx) não gera duas tentativas inúteis.
- A escrita não duplica locação, mesmo com rental-service instável.
- Prazos por ambiente sem tocar em código de regra.

**Ruins.**

- Uma consulta de disponibilidade contra o inventory fora do ar custa 3 idas (1 tentativa + 2 repetições). Com inventory fora, o custo se multiplica por 3; é preciso observar taxa de erro e latência antes de escolher circuit breaker.
- 503 é uma resposta nova para quem consome `GET /reservations/availability`; clientes precisam tratá-la explicitamente.
- A dívida de `CreateReservation` (reserva `PENDING` órfã quando a escrita falha) continua aberta e é visível agora, porque a falha deixa de ser engolida.

## Evidência

| Teste | O que fixa |
|---|---|
| `RentalRestGatewayFaultToleranceTest.shouldTimeOutWhenRentalServiceNeverResponds` | deadline da escrita vale e propaga `TimeoutException`, com uma única chamada |
| `RentalRestGatewayFaultToleranceTest.shouldNotRetryRentalStartWhenItFails` | escrita não repete: exatamente uma chamada em falha |
| `RentalRestGatewayFaultToleranceTest.shouldAbortTheInFlightWriteBeforeTheFaultToleranceDeadline` | prazo de transporte menor que o deadline de FT |
| `RentalRestGatewayFaultToleranceTest.shouldStartTheRentalWhenTheServiceAnswersInTime` | caminho feliz não foi quebrado |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldRetryTransientInventoryFailureAndThenReturnVehicles` | 5xx transitório é repetido e a leitura se recupera na 3ª tentativa |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldNotRetryWhenInventoryRejectsTheQuery` | 4xx não é repetido |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldFailWithInventoryUnavailableWhenTheReadKeepsTimingOut` | deadline estourado esgota as tentativas e vira `InventoryUnavailable`, não lista vazia |
| `GraphQLInventoryGatewayFaultToleranceTest.shouldReturnTheVehiclesWhenInventoryAnswersInTime` | resposta normal preservada |
| `FindAvailableVehiclesTest` | `[]` continua sendo resultado de negócio legítimo; `InventoryUnavailable` é sinal |
| `AvailabilityUnavailableTest` (3 testes) | 503 com `Retry-After` e corpo estável; 200 quando o inventory responde; sem vazar detalhe de infraestrutura |
| `StartRentalTest.shouldCreateAnotherRentalForTheSameReservationWhenCalledTwice` | caracterização da não-idempotência que proíbe retry na escrita |

## Relação com outras decisões

- **ADR 002 (retry de mensagens)** — mesma conclusão em outro meio: retry só é seguro onde a operação é idempotente. Ali a garantia vem de `eventId`; aqui a leitura é idempotente por natureza e a escrita simplesmente não é repetível.
- **ADR 003 (DLQ)** — lá o destino da falha esgotada é uma fila morta. Aqui o destino é um sinal HTTP, porque a falha é de uma requisição síncrona que o cliente ainda está esperando.
- **ADR 008 (outbox)** — a dívida da escrita (reserva `PENDING` após falha) pede uma solução de saga/outbox para a transação entre contextos, que é o próximo tipo de decisão e não cabe aqui.

## Estado da decisão

**Accepted / implementada**, com evidência de teste unitário dos adapters e de contrato da fronteira. Falha em integração real entre os serviços (container fora do ar) permanece como evidência do item 9, junto de circuit breaker e bulkhead.
