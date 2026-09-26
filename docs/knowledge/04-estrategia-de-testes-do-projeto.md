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
| é o artefato/runtime | Native | `@QuarkusIntegrationTest` | 🔜 [roadmap](../roadmap.md) |

## 2. As três regras que mais violamos

### 2.1 Domínio: JUnit puro, sem nada do Quarkus

Proibido em `src/test/java/**/domain/`: `@QuarkusTest`, `@Inject`, Panache, Mutiny,
`jakarta.*`, `io.quarkus.*`.

```java
// ✅ evidence
class InvoiceTest {
    @Test
    void shouldStartAsDraft() { ... }
}
```

Motivo: se o teste de domínio precisa subir a aplicação, o domínio já está acoplado.

### 2.2 Aplicação: fake explícito da porta, nunca mock de framework

Porta de saída → implementação `static class Fake...` dentro do próprio teste.

```java
static class FakeRepository implements InvoiceRepository {
    Invoice saved;
    public Uni<Invoice> save(Invoice invoice) {
        saved = invoice;
        return Uni.createFrom().item(invoice);
    }
    ...
}
```

Fontes: `billing-service/src/test/java/org/acme/billing/application/CreateInvoiceTest.java`,
`users-service/src/test/java/org/acme/users/application/ReservationFacadeTest.java`.

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
            new ObjectMapper().findAndRegisterModules(),
            event -> { received.set(event); return Uni.createFrom().voidItem(); });
}
```

O seam existe porque o consumer tem um construtor **package-private** que recebe a
função de handler. Isso é deliberado: permite testar a tradução sem subir Kafka.

Fontes: `KafkaVehicleRegisteredConsumerTest`, `KafkaReservationConfirmedConsumerTest`,
`KafkaRentalCompletedConsumerTest`, `KafkaEventPublisherTest` (com `RecordingEmitter`).

**(b) com `@QuarkusTest` + infraestrutura real** — quando a fronteira é o comportamento:

| Teste | Fronteira provada |
|---|---|
| `BillingFlowKafkaIntegrationTest` | Kafka → consumer → inbox → Postgres, DRAFT→OPEN |
| `OutboxRelayKafkaIntegrationTest` | outbox → relay → `invoice-opened`, com **chave** Kafka |
| `DelayedRetryKafkaIntegrationTest` | `delayed-retry-topic`: NACK → 3 tentativas → exaustão |
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

No histórico git do projeto isso aparece como commits separados:

```text
test(billing): consume outbox event from latest Kafka offset
fix(billing): move Kafka offset lookup off event loop
```

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
| retry/backoff com jitter em asserção | `onFailure().retry().withBackOff(...).withJitter(...)` | `BillingFlowKafkaIntegrationTest.awaitInvoiceOpenedAfter` |

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
      Estado real: 87 métodos de teste, sendo 11 com `Quando`/`When` (o caso mais explícito,
      `shouldCommitInvoiceAndOutboxTogetherWhenInvoiceIsOpened`), 75 com `should` sem
      condição e 1 legado (`testStagingProfileOverridesGraphQLUrl`). É **preferência**, não
      invariante — ver a regra em [testing.md](../testing.md) §Nomenclatura.
- [ ] Teste novo de comportamento tem o `RED` visível no histórico.

## 9. Inventário de testes por camada

| Camada | Testes |
|---|---|
| Domain (8) | `InvoiceTest`, `InvoiceLineTest`, `VehicleTest`, `VehicleDailyRateTest`, `VehicleTelemetryTest`, `MaintenanceOrderTest`, `RentalTest`, `ReservationTest` |
| Application (19) | `CreateInvoiceTest`, `OpenInvoiceForRentalTest`, `ConsumeVehicleRegisteredTest`, `ConsumeReservationConfirmedTest`, `ConsumeRentalCompletedTest`, `PublishPendingOutboxEventsTest`, `RegisterVehicleTest`, `RegisterVehicleEventTest`, `RegisterVehicleRetryTest`, `RegisterVehicleTimeoutTest`, `BulkRegisterVehiclesTest`, `BulkRegisterVehiclesBackpressureTest`, `BulkRegisterVehiclesCancellationTest`, `DecommissionVehicleTest`, `FindVehicleByPlateTest`, `SearchVehiclesTest`, `StartRentalTest`, `CreateReservationTest`, `ReservationFacadeTest` |
| Adapter unit (4) | `KafkaVehicleRegisteredConsumerTest`, `KafkaReservationConfirmedConsumerTest`, `KafkaRentalCompletedConsumerTest`, `KafkaEventPublisherTest` |
| Adapter/Integration (12) | `ReservationResourceTest`, `ReservationPersistenceTest`, `ReactiveExecutionResourceTest`, `StagingTest`, `BillingPersistenceTest`, `BillingOutboxIntegrationTest`, `BillingFlowKafkaIntegrationTest`, `DelayedRetryKafkaIntegrationTest`, `DlqKafkaIntegrationTest`, `TransactionalInboxRetryKafkaIntegrationTest`, `TransactionalInboxProcessorIntegrationTest`, `OutboxRelayKafkaIntegrationTest` |

**Total: 44 classes de teste** (43 `@QuarkusTest`/JUnit + 1 `@QuarkusIntegrationTest`).
`BillingPersistenceTest` é `@QuarkusTest` (Postgres via Dev Services), portanto está em
Adapter/Integration e **não** em "Adapter unit" — a camada "adapter unit" é JUnit puro,
sem container.

**Veja também:** [03-testes-quarkus.md](./03-testes-quarkus.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md) ·
[10-exemplos-contrarios-ao-dominio.md](./10-exemplos-contrarios-ao-dominio.md)

---

_Última atualização: 2026-09-26 (padrão derivado das correções de `billing-service`,
commit `a6fddc0`, e do inventário conferido: 44 classes de teste)._
