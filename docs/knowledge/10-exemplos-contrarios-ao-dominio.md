# 10 — Exemplos contrários ao domínio

> **Documento de estudo comparativo.**
> Todo código aqui é **🧩 didático**: existe para mostrar *o que não fazer*.
> Nenhum bloco deste arquivo pode chegar a `src/main`.
> Se um exemplo "bonsito" quebrar as regras, ele quebra as regras.

Cada exemplo segue o mesmo formato:

```text
❌ O código errado (e por que ele parece certo à primeira vista)
✅ O código do padrão deste repositório
🔎 O que quebra quando o errado está em produção
```

Regra de ouro citada em todo o arquivo
([ddd-tdd-standards.md](../ddd-tdd-standards.md) §3.4):

> Domain code must not import framework, transport or persistence types:
> `jakarta.*`, `io.quarkus.*`, `io.smallrye.mutiny.*`, REST/GraphQL/gRPC types,
> Panache/JPA/Mongo, HTTP exceptions, transport DTOs.

---

## 1. Entidade de domínio que também é entidade de persistência

### ❌ O errado (estilo active record do capítulo 7)

```java
// 🧩 DIDÁTICO — NÃO COPIAR
package org.acme.billing.domain.model;

import io.quarkus.hibernate.orm.panache.PanacheEntity;
import jakarta.persistence.Entity;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

@Entity
public class Invoice extends PanacheEntity {   // Panache no domínio
    @NotNull
    public String customerId;                  // campo público
    @NotNull
    public String reservationId;
    public BigDecimal total;
    public InvoiceStatus status;

    public void markPaid() {
        this.status = InvoiceStatus.PAID;     // setter público implícito
        persist();                             // domínio salvando no banco
    }
}
```

Por que parece certo: é o exemplo mais curto do capítulo, e `persist()` funciona.

### ✅ O padrão do projeto

```java
// billing-service/src/main/java/org/acme/billing/domain/model/Invoice.java
// sem Panache, sem jakarta, sem public field, sem setter
public final class Invoice {
    private final InvoiceId id;
    private final String customerId;
    private final String reservationId;
    private final List<InvoiceLine> lines;
    private InvoiceStatus status;          // única parte mutável

    public Invoice markPaid() {            // comando que devolve o agregado
        this.status = InvoiceStatus.PAID;
        return this;
    }

    public Money total() { ... }           // total é DERIVADO, não campo
}
```

Note que `total()` é **método**, não campo: o total é derivado das linhas, e por isso não
pode divergir delas. É o tipo de invariante que um `public BigDecimal total` destrói.

```java
// adapter/out/persistence/PanacheInvoiceRepository.java
@ApplicationScoped
public class PanacheInvoiceRepository implements InvoiceRepository {
    // aqui vivem @Entity, persist(), PanacheQuery
}
```

### 🔎 O que quebra

1. **Invariante impossível de garantir.** `setStatus(PAID)` público ignora as regras do
   agregado. Com `public` field, qualquer código pode escrever direto.
2. **Teste de domínio precisa de banco.** O teste de `Invoice` passa a exigir container →
   a camada mais barata de testar vira a mais cara.
3. **Schema vaza para o domínio.** Renomear coluna é refatoração de domínio.
4. **Não compila em nativo** se reflection não estiver registrada.

---

## 2. Caso de uso que conhece a biblioteca

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
@ApplicationScoped
public class CreateInvoice {
    @Inject PanacheInvoiceRepository repository;   // adaptador injetado no caso de uso
    @Inject EntityManager em;                      // JPA no caso de uso

    public Invoice create(String customerId, List<InvoiceLine> lines) {
        Invoice invoice = new Invoice();
        em.persist(invoice);
        return invoice;
    }
}
```

### ✅ O padrão do projeto

```java
@ApplicationScoped
public class CreateInvoice {
    private final InvoiceRepository repository;   // porta

    public CreateInvoice(InvoiceRepository repository) { this.repository = repository; }

    public Uni<Invoice> handle(Command command) { ... }
}
```

### 🔎 O que quebra

- O caso de uso **deixa de ser testável em JVM puro** (precisa de Panache).
- A regra 2 do [AGENTS.md](../../AGENTS.md) é violada: domínio/aplicação não dependem de
  Quarkus/Panache/JPA.
- Trocar Panache por outro mecanismo passa a exigir mexer na aplicação.

---

## 3. Regra de negócio dentro do resource REST

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
@Path("/invoices")
public class InvoiceResource {
    @GET
    public List<Invoice> list(@QueryParam("status") String status,
                              @QueryParam("page") int page,
                              @QueryParam("size") int size,
                              @QueryParam("sort") String sort) {
        return Invoice.find("status", status)          // Panache no inbound adapter
                .page(Page.of(page, size))
                .list("sort");
    }
}
```

### ✅ O padrão do projeto

```text
GraphQL input
   ↓ adapter mapeia entrada
VehicleSearch (query de aplicação)
   ↓
SearchVehicles (caso de uso)
   ↓
VehiclePage
   ↓ adapter mapeia saída
```

### 🔎 O que quebra

- Filtro, ordenação e paginação **viram comportamento do transporte**: dois transportes
  (REST e GraphQL) duplicam a regra e divergem.
- A regra 10 do [AGENTS.md](../../AGENTS.md) é violada diretamente.
- Testar a regra exige subir HTTP.

---

## 4. `LocalDate.now()` dentro do domínio

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
@Entity
public class Reservation {
    public boolean isOverdue() {
        return endDay.isBefore(LocalDate.now());   // domain lendo o relógio do sistema
    }
}
```

### ✅ O padrão do projeto

```java
public boolean isOverdue(LocalDate today) {
    return endDay.isBefore(today);
}
```

```java
// camada de aplicação: injeta o tempo no caso de uso
public Uni<Reservation> handle(Command command, LocalDate today) { ... }
```

### 🔎 O que quebra

- Teste **depende da data real**. Um teste que passa hoje falha no dia 1º do mês.
- Não é determinístico em reprodutibilidade de bug.
- Regra explícita: *"Time-sensitive domain rules receive the relevant date/time from the
  application layer rather than calling `now()` directly."*

---

## 5. Consumer de Kafka com `void` e log

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
@Incoming("reservation-confirmed-in")
public void consume(String payload) {
    try {
        var event = mapper.readValue(payload, ReservationConfirmed.class);
        createInvoice.handle(event);
    } catch (Exception e) {
        log.error("failed", e);     // falha engolida
    }
}
```

### ✅ O padrão do projeto

```java
@Incoming("reservation-confirmed-in")
public Uni<Void> consume(String payload) {
    return Uni.createFrom()
            .item(() -> deserialize(payload))          // falha aqui = NACK
            .flatMap(event -> inboxProcessor.process(
                    event.eventId(),
                    () -> consumer.handle(event)))     // falha aqui = NACK
            .onFailure().invoke(f -> LOG.errorf("...", f.toString(), f));
}
```

### 🔎 O que quebra

- `void` + `try/catch` que engole ⇒ o broker recebe **ack** de uma mensagem **não
  processada**. O evento some em silêncio.
- Não há idempotência: redelivery manual duplica a invoice.
- Retry e DLQ nunca disparam, porque nunca houve `nack`.

> Este é o exemplo mais perigoso da lista, porque **parece** robusto: tem log, tem
> tratamento de erro, tem `try/catch`.

---

## 6. Publicar no Kafka depois de commitar

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
public Uni<Invoice> handle(Command command) {
    return repository.save(invoice)
            .call(saved -> emitter.send(new InvoiceOpened(saved.id())))  // fora da transação
            .onFailure().recoverWithNull();   // "não é grave"
}
```

### ✅ O padrão do projeto

```text
@Transactional (uma só)
   repository.save(invoice)
   outbox.append(invoiceOpenedEvent)      // mesma transação
   ↓ commit
   OutboxRelay (async)  →  Kafka
```

### 🔎 O que quebra

- Kafka fora do ar depois do commit ⇒ **estado existe, evento nunca existiu**.
- `recoverWithNull` esconde justamente esse caso.
- O consumidor nunca vai saber que o evento faltou.

Detalhes: [09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md) §5.

---

## 7. Teste que esconde contrato assíncrono

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
@Test
@RunOnVertxContext
void shouldPublishInvoiceOpened() {
    relay.relay().await().indefinitely();   // await no event loop
    // o teste "passa" mas a thread é bloqueada; se o relay estiver na event loop,
    // o comportamento real (produção) nunca é exercitado
    assertTrue(companion.consumeStrings().fromTopics("invoice-opened", 1) != null);
}
```

### ✅ O padrão do projeto

```java
@Test
@RunOnVertxContext
void shouldPublishInvoiceOpenedFromOutboxToKafka(UniAsserter asserter) {
    asserter.assertThat(
            () -> onWorkerThread(() -> companion.offsets().get(partition, OffsetSpec.latest()).offset())
                    .emitOn(eventLoop)                              // volta pro event loop
                    .flatMap(endOffset -> relay.relay().replaceWith(endOffset))
                    .flatMap(endOffset -> onWorkerThread(() -> awaitInvoiceOpenedAfter(partition, endOffset, invoiceId.get()))),
            record -> {
                assertEquals(invoiceId.get(), record.key());
                assertTrue(record.value().contains(reservationId));
            });
}
```

### 🔎 O que quebra

- O teste passa e a aplicação falha com `HR000068` em produção.
- Ou o inverso: o teste **trava** e ninguém sabe se é flaky ou quebrado.
- E o pior: o teste passa sempre, sem provar nada (exemplo do `fromTopics(...) != null`).

---

## 8. Compartilhar entidade entre contextos

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
// inventory-service/src/main/java/org/acme/shared/domain/Vehicle.java
package org.acme.shared.domain;          // "shared" — o nome é o aviso

@Entity
public class Vehicle { ... }             // usado por inventory, reservation e rental
```

### ✅ O padrão do projeto

```text
inventory   → domain/model/Vehicle                    (agregado do Inventory)
reservation → adapter/out/inventory/model/Car         (DTO do client GraphQL)
reservation → application/query/AvailableVehicle     (visão da query de disponibilidade)
users       → application/model/ReservationView      (visão do BFF)
rental      → domain/model/ReservationId              (só a identidade da reserva)
```

Cada contexto tem **seu** modelo. A comunicação é por **contrato explícito** (REST, GraphQL,
gRPC, Kafka) com **anti-corruption adapter**. `docs/domain.md` §7 lista exatamente o que
**não** é compartilhado.

### 🔎 O que quebra

- Um campo novo em `Vehicle` vira alteração em 3 serviços.
- Conflito de versionamento: cada contexto evolui no seu ritmo.
- Viola as regras 5 e 6 do [AGENTS.md](../../AGENTS.md).

---

## 9. `@Transactional` em volta de um `Uni` já composto

### ❌ O errado

```java
// 🧩 DIDÁTICO — NÃO COPIAR
@Transactional
public Uni<Invoice> handle(Command command) {
    return repository.save(invoice)
            .flatMap(saved -> otherService.call(saved))  // I/O externo dentro da transação
            .map(saved -> { repository.update(saved); return saved; });
}
```

### ✅ O padrão do projeto

```java
@WithTransaction
public Uni<Invoice> handle(Command command) {
    return repository.save(invoice)
            .onItem().invoke(saved -> outbox.append(toEvent(saved)));
}
```

I/O externo **não** fica dentro da transação. A fronteira transacional é
`@WithTransaction` do Hibernate Reactive, e o commit acontece no **fim da pipeline**.

### 🔎 O que quebra

- A transação fica aberta durante a chamada externa (conexão de banco retida, locks).
- Se a chamada externa demorar, o `pool` de conexões estoura.
- Rollback por falha remota desfaz trabalho local já válido.

---

## 10. Tabela de exceções: o que é errado e onde está proibido

| # | Padrão errado | Proibido por |
|---|---|---|
| 1 | `domain` importando `io.quarkus.*` / `jakarta.*` / Panache / Mutiny | regra 2 do AGENTS.md |
| 2 | caso de uso injetando repositório concreto | regra 4 (infra em adapter) + §5 do padrão |
| 3 | filtro/ordem/paginação no resource | regra 10 |
| 4 | `LocalDate.now()` no domínio | §3.4 do padrão |
| 5 | `@Incoming` com `void` e catch genérico | §8 do padrão (falha/idempotência) |
| 6 | publicar no broker fora da transação | ADR 004/008 |
| 7 | `await().indefinitely()` em caminho reativo | [testing.md](../testing.md) §Reactive testing + [04](./04-estrategia-de-testes-do-projeto.md) §5 |
| 8 | pacote `shared`/`common` de domínio entre contextos | regras 5 e 6 |
| 9 | transação envolvendo I/O externo | regra 13 do AGENTS.md + [architecture.md](../architecture.md) + ADR 004 |
| 10 | regra de negócio em pacote `util`/`common` genérico | regra 8 (o `domain/service/` do blueprint **é** legítimo) |

---

## 11. Checklist de estudo

- [ ] Sei explicar por que "entidade Panache = entidade de domínio" é um problema de
  **testabilidade** antes de ser de estética.
- [ ] Sei dizer qual regra do AGENTS.md cada um dos 9 exemplos viola (regras 2, 4, 10 e 13 do AGENTS.md + §3.4/§5/§8 do padrão).
- [ ] Sei reescrever o exemplo 5 (`void` + catch) retornando `Uni<Void>`.
- [ ] Sei explicar por que o exemplo 7 pode passar sem provar nada.
- [ ] Sei dizer por que o exemplo 6 não é resolvido com `recoverWithNull`.

**Contagem:** 9 exemplos + 1 tabela-resumo (§10).

**Veja também:** [04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) ·
[06-persistencia-transacoes-e-nosql.md](./06-persistencia-transacoes-e-nosql.md) ·
[08-messaging-reativo.md](./08-messaging-reativo.md)

---

_Última atualização: 2026-09-26 (9 exemplos + tabela-resumo, derivados das decisões dos
capítulos 7, 8 e 9 e do padrão DDD/TDD do repositório; blocos "✅" conferidos contra o
código real)._
