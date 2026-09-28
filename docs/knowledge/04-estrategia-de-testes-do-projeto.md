# 04 — Estratégia de testes deste projeto

> **Foco principal desta base de estudo.**
> Norma: [testing.md](../testing.md) + [ddd-tdd-standards.md](../ddd-tdd-standards.md) §7.
> Framework: [03-testes-quarkus.md](./03-testes-quarkus.md).

Este documento é o **padrão do repositório**. Ele existe porque o capítulo 5 ensina um
conjunto de ferramentas e o projeto precisa de um **critério de camada**. Sem o critério,
"teste bom" vira opinião.

## 1. As cinco camadas

```text
              Integration / Native        @QuarkusIntegrationTest
                     ▲
             Adapter / Contract          @QuarkusTest
                     ▲
            Application Use Cases        JUnit puro + fakes
                     ▲
               Domain tests              JUnit puro, sem framework
```

A pergunta que decide a camada é **uma só**:

> O framework, o protocolo ou a fronteira fazem parte do comportamento que eu quero provar?

| Se a resposta é... | Camada | Ferramenta | Exemplo real do projeto |
|---|---|---|---|
| não, é regra/invariante | Domain | JUnit puro | `InvoiceTest`, `VehicleTest`, `RentalTest`, `ReservationTest` |
| não, é orquestração | Application | JUnit + fake da porta | `CreateInvoiceTest`, `StartRentalTest`, `ReservationFacadeTest` |
| sim, é o adapter | Adapter | `@QuarkusTest` | `ReservationResourceTest`, `ReactiveExecutionResourceTest` |
| sim, e é infra real | Integration | `@QuarkusTest` + Dev Services | `BillingFlowKafkaIntegrationTest`, `TransactionalInboxProcessorIntegrationTest` |
| é o artefato/runtime | Native | `@QuarkusIntegrationTest` | ⚠️ parcial: `ReservationResourceIT` existe e roda; native ainda é 🔜 [roadmap](../roadmap.md) |

## 2. As três regras que mais violamos

### 2.1 Domínio: JUnit puro, sem nada do Quarkus

Proibido em `src/test/java/**/domain/`: `@QuarkusTest`, `@Inject`, Panache, Mutiny,
`jakarta.*`, `io.quarkus.*`.

```java
// ✅ evidence — billing-service/src/test/java/org/acme/billing/domain/InvoiceTest.java
class InvoiceTest {
    @Test
    void shouldOpenDraftInvoice() { ... }        // real
    @Test
    void shouldNotPayDraftInvoice() { ... }       // real
    @Test
    void shouldNotReplaceLinesOnOpenInvoice() { ... }
}
```

Motivo: se o teste de domínio precisa subir a aplicação, o domínio já está acoplado.

### 2.2 Aplicação: fake explícito da porta, nunca mock de framework

Porta de saída → implementação `static class Fake...` dentro do próprio teste.

`billing-service/.../application/CreateInvoiceTest.java` — fake aninhado, com os dois
métodos que o caso de uso exercise:

```java
static class FakeRepository implements InvoiceRepository {
    Invoice saved;
    public Uni<Invoice> save(Invoice invoice) { saved = invoice; return Uni.createFrom().item(invoice); }
    public Uni<Optional<Invoice>> findByReservationId(String reservationId) {
        return Uni.createFrom().item(Optional.ofNullable(saved));
    }
}
```

O mesmo desenho aparece em `users-service/.../application/ReservationFacadeTest.java`:
o fake implementa a **porta** do contexto, nunca um tipo de framework.

**Por que fake e não Mockito em aplicação:**

1. a porta é **nossa**, então uma classe anônima é mais barata de ler;
2. `Uni`/`Multi` de fake é trivial; mockear tipo genérico com Mockito é mais verboso;
3. fake **documenta a porta**. Se a porta crescer, o fake quebra na compilação — o que é
   o comportamento desejado.

### 2.3 Adapter: `@QuarkusTest` só quando a fronteira é o comportamento

Adapter testado de duas formas no projeto:

**(a) sem Quarkus, com seam de construtor** — quando a lógica é tradução/serialização:

```java
class KafkaVehicleRegisteredConsumerTest {
    KafkaVehicleRegisteredConsumer consumer = new KafkaVehicleRegisteredConsumer(
            new EventJsonCodec(new ObjectMapper().findAndRegisterModules()),
            event -> { received.set(event); return Uni.createFrom().voidItem(); });
}
```

O seam existe porque o consumer tem um construtor **package-private** que recebe a
função de handler. Isso é deliberado: permite testar a tradução sem subir Kafka.
A serialização/deserialização fica isolada no `EventJsonCodec` de cada contexto
(inventory: encode-only; billing: decode-only), testado em JUnit puro.

Fontes: `KafkaVehicleRegisteredConsumerTest`, `KafkaReservationConfirmedConsumerTest`,
`KafkaRentalCompletedConsumerTest`, `VehicleRegisteredEventPublisherTest` (com `RecordingEmitter`),
`EventJsonCodecTest` (inventory e billing).

**(b) com `@QuarkusTest` + infraestrutura real** — quando a fronteira é o comportamento:

| Teste | Fronteira provada |
|---|---|
| `BillingFlowKafkaIntegrationTest` | Kafka → consumer → inbox → Postgres, DRAFT→OPEN |
| `OutboxRelayKafkaIntegrationTest` | outbox → relay → `invoice-opened`, com **chave** Kafka |
| `DelayedRetryKafkaIntegrationTest` | `delayed-retry-topic`: NACK → **4 entregas** (1 inicial + `max-retries=3`) → exaustão. ⚠️ roda no canal sintético `retry-exhaustion-in`, **não** em `vehicle-registered-in` |
| `DlqKafkaIntegrationTest` | esgotamento → DLQ (ADR 003) |
| `TransactionalInboxRetryKafkaIntegrationTest` | inbox transacional sob retry |
| `TransactionalInboxProcessorIntegrationTest` | claim + efeito na **mesma** transação |
| `ReactiveExecutionResourceTest` | event loop × worker pool |
| `ReservationPersistenceTest` | Panache/Hibernate Reactive + schema |

## 3. Nomenclatura (obrigatória)

```java
void shouldRejectReservationWhenVehicleIsAlreadyReserved() { ... }
```

Regras:

- comece com `should`;
- descreva **comportamento**, não implementação (`shouldCallRepositoryOnInvoice` é
  implementação; `shouldPersistDraftInvoice` é comportamento);
- um `should` por comportamento observável;
- para casos negativos, nomeie a condição: `shouldFailWhenPayloadCannotBeDeserialized`.

## 4. Evidência TDD

Um teste escrito **depois** da implementação não é evidência de TDD. A trilha esperada:

```text
Domain Design → RED → GREEN → REFACTOR → Adapter/Integration evidence → guardians
```

### 4.1 Qual é a evidência real (verificada no `git log`)

🔴 **Honestidade primeiro:** a evidência de RED **não é uniforme** no repositório.

| Serviço | Par `test → feat` no histórico | RED visível? |
|---|---|---|
| `inventory-service` | `e424b28` → `c7e158f` (telemetria/manutenção)<br>`b877a55` → `48cd8c1` (queries)<br>`0cc22dd` → `14b3b61` (registro reativo)<br>`f21735f` → `3ebcfce` (probe de execução) | ✅ **sim** |
| `billing-service` | `96d88f0 feat(cap9)` criou **9 arquivos de teste no mesmo commit** | ❌ **não** (test-after-impl) |
| `billing-service` (outbox) | `b4fcda5 feat` → `40bf485 test` | ❌ test-after-impl |
| `rental-service`, `users-service` | sem par algum | ❌ não aplicável (1 teste cada) |

⚠️ **Os commits `a473361` (`test(billing): consume outbox event from latest Kafka offset`)
e `bda399d` (`fix(billing): move Kafka offset lookup off event loop`) NÃO são um par
Red→Green.** Ambos tocam **só** `OutboxRelayKafkaIntegrationTest.java`: é reparo de
harness de teste, não especificação. O exemplo limpo para citar é `f642afa`
(`test(billing): avoid Kafka topic mutation in relay test`) — um arquivo, mensagem
descritiva, e o conteúdo sustenta literalmente a lição de
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md).

⚠️ `a6fddc0 fix tests` é um bundle de 7 arquivos (inclui renomear
`BillingFlowKafkaCompanionResource` → `BillingKafkaCompanionResource`) e **não** segue a
convenção `test()/feat()/fix()/refactor()`. Serve como evidência do *problema*, não da
prática.

Preferência: `test(...)` → `feat(...)` → `refactor(...)` em commits pequenos.
Ver [roadmap.md](../roadmap.md) para a regra de evolução.

## 5. Testes reativos (a parte que mais machuca)

### 5.1 Regra

> Não use `await().indefinitely()` para esconder um contrato assíncrono em código que
> deveria permanecer não bloqueante.

`await()` é aceitável **apenas** em teste de aplicação/adapter que roda fora do event loop
e não está sob assertor. Quando o teste roda **no event loop** (`@RunOnVertxContext`),
`await()` é proibido — o event loop é o mesmo do teste.

### 5.2 O par correto

```java
@Test
@RunOnVertxContext
void shouldPublishInvoiceOpenedFromOutboxToKafka(UniAsserter asserter) {
    asserter.assertThat(supplier, value -> assertEquals(expected, value));
}
```

Complementos que o projeto usa:

| Necessidade | Ferramenta | Onde |
|---|---|---|
| encadear asserções no event loop | `UniAsserter` | testes de billing |
| ler o banco **fora** de transação envolvente | `UniAsserter` | `BillingFlowKafkaIntegrationTest` |
| assertar item/falha de `Uni` | `UniAssertSubscriber` | `RegisterVehicleTimeoutTest`, `RegisterVehicleRetryTest` |
| controlar demanda e backpressure | `AssertSubscriber` | `BulkRegisterVehiclesBackpressureTest` |
| retry/backoff com jitter em asserção | `onFailure().retry().withBackOff(...).withJitter(...)` | `BillingFlowKafkaIntegrationTest.awaitInvoiceStatus` |

### 5.3 Por que `UniAsserter` e não `TransactionalUniAsserter` no `BillingFlowKafkaIntegrationTest`

Achado real, documentado em [roadmap.md](../roadmap.md):

- `TransactionalUniAsserter` abre uma transação que **envolve** as asserções;
- o Hibernate Reactive mantém **cache de primeira camada** por sessão;
- o consumer já tinha commitado o `UPDATE` de status, mas a leitura seguinte, na mesma
  sessão, via o **estado do DRAFT**;
- resultado: o teste "enxergava" o valor antigo e mascarava o bug.

`UniAsserter` (sem transação envolvente) faz cada leitura enxergar o que foi commitado.
Não é preferência estética: é **correção do teste**.

## 6. Isolamento entre testes de broker

Problema: broker é estado global. A estratégia do projeto:

1. **um** `KafkaCompanionResource` para a suíte inteira
   (`restrictToAnnotatedClass = false`);
2. tópicos **pré-criados** no `start()`, incluindo os de retry/DLQ do perfil `%test`;
3. **scheduler desligado** em `%test` (`quarkus.scheduler.enabled=false`) para o
   `OutboxRelay` não publicar em background;
4. **offsets** como critério de leitura, não "último registro do tópico" — ver
   [11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md).

Detalhe completo: [08-messaging-reativo.md](./08-messaging-reativo.md) §5.

## 7. Como decidir a camada em 30 segundos

```text
O teste precisa de CDI, HTTP, banco ou broker?
   ├─ não → a regra testada é de domínio ou orquestração?
   │        ├─ invariante/regra de negócio  → Domain (JUnit puro)
   │        └─ coordena casos de uso/portas → Application (JUnit + fake)
   └─ sim → a fronteira É o comportamento sob teste?
            ├─ sim → Adapter / Integration (@QuarkusTest)
            └─ não → está testando o framework, não o sistema. Não escreva.
```

## 8. Checklist de revisão

- [ ] Nenhum import de `io.quarkus`, `jakarta`, Panache ou Mutiny em teste de domínio.
- [ ] Teste de aplicação usa fake da porta, não mock de framework.
- [ ] `@QuarkusTest` só onde a fronteira é o comportamento.
- [ ] Nenhum `await().indefinitely()` em teste anotado com `@RunOnVertxContext`.
- [ ] Todo teste Kafka declara `restrictToAnnotatedClass = false`.
- [ ] Nomes no formato `should<Comportamento>Quando<Condição>` **quando a condição importar**.
      Estado real: 131 métodos de teste verificados por varredura de assinatura, sendo 16 com
      condição `When` (o caso mais explícito, `shouldCommitInvoiceAndOutboxTogetherWhenInvoiceIsOpened`),
      114 com `should` sem condição e 1 legado (`testStagingProfileOverridesGraphQLUrl`). É
      **preferência**, não invariante — ver a regra em [testing.md](../testing.md) §Nomenclatura.
- [ ] Teste novo de comportamento tem o `RED` visível no histórico.

## 9. Inventário de testes por camada

| Camada | Testes |
|---|---|
| Domain (8) | `InvoiceTest`, `InvoiceLineTest`, `VehicleTest`, `VehicleDailyRateTest`, `VehicleTelemetryTest`, `MaintenanceOrderTest`, `RentalTest`, `ReservationTest` |
| Application (19) | `CreateInvoiceTest`, `OpenInvoiceForRentalTest`, `ConsumeVehicleRegisteredTest`, `ConsumeReservationConfirmedTest`, `ConsumeRentalCompletedTest`, `PublishPendingOutboxEventsTest`, `RegisterVehicleTest`, `RegisterVehicleEventTest`, `RegisterVehicleRetryTest`, `RegisterVehicleTimeoutTest`, `BulkRegisterVehiclesTest`, `BulkRegisterVehiclesBackpressureTest`, `BulkRegisterVehiclesCancellationTest`, `DecommissionVehicleTest`, `FindVehicleByPlateTest`, `SearchVehiclesTest`, `StartRentalTest`, `CreateReservationTest`, `ReservationFacadeTest` |
| Adapter unit (8) | `KafkaVehicleRegisteredConsumerTest`, `KafkaReservationConfirmedConsumerTest`, `KafkaRentalCompletedConsumerTest`, `VehicleRegisteredEventPublisherTest`, `EventJsonCodecTest` (inventory), `EventJsonCodecTest` (billing), `MicrometerOutboxMetricsTest`, `MicrometerInventoryMetricsTest` |
| Adapter/Integration (21) | `ReservationResourceTest`, `ReservationPersistenceTest`, `ReactiveExecutionResourceTest`, `StagingTest`, `BillingPersistenceTest`, `BillingOutboxIntegrationTest`, `BillingFlowKafkaIntegrationTest`, `DelayedRetryKafkaIntegrationTest`, `DlqKafkaIntegrationTest`, `TransactionalInboxRetryKafkaIntegrationTest`, `TransactionalInboxProcessorIntegrationTest`, `OutboxRelayKafkaIntegrationTest`, `OutboxMetricsIntegrationTest`, `VehicleRegisteredEventPublisherIntegrationTest`, `BusinessMetricsIntegrationTest`, `VehicleEntityMappingTest`, `HealthEndpointTest` (billing), `HealthEndpointTest` (inventory), `HealthEndpointTest` (rental), `HealthEndpointTest` (reservation), `HealthEndpointTest` (users) |

**Total: 57 classes de teste** = 21 `@QuarkusTest` + 35 JUnit puro + 1 `@QuarkusIntegrationTest`
(`ReservationResourceIT`).
`BillingPersistenceTest` é `@QuarkusTest` (Postgres via Dev Services), portanto está em
Adapter/Integration e **não** em "Adapter unit" — a camada "adapter unit" é JUnit puro,
sem container. `MicrometerOutboxMetricsTest` e `MicrometerInventoryMetricsTest` são JUnit puro
com um `SimpleMeterRegistry` real (shape inaugurada pelo cap. 10: adapter de métrica testado
sem Quarkus — ver [14-cloud-native-patterns.md](./14-cloud-native-patterns.md) §4 e
[testing.md](../testing.md) §Observability).

**Veja também:** [03-testes-quarkus.md](./03-testes-quarkus.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md) ·
[10-exemplos-contrarios-ao-dominio.md](./10-exemplos-contrarios-ao-dominio.md)

---

_Última atualização: 2026-09-28 (padrão derivado das correções de `billing-service`,
commit `a6fddc0`, e do inventário conferido: 57 classes de teste; cap. 10 acrescenta a
shape de teste de adapter de métrica com `SimpleMeterRegistry` puro, no billing e no inventory)._
