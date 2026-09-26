# 06 — Persistência, transações e dados reativos

> Capítulo 7 do *Quarkus in Action*.
> Fato operacional: [services.md](../services.md) §Adapters e §Dependency rule.

## 1. O que o livro ensina

O capítulo 7 oferece **três** estilos de acesso a dados, e a escolha é de estilo, não de
capacidade:

| Estilo | Trade-off | Onde o projeto usa |
|---|---|---|
| **Active record** (Panache na entidade) | menos código, entidade vira repositório | 🧩 só em exercício de framework (MongoDB, panache-model) |
| **Repository pattern** (Panache em classe separada) | domínio não "vira" repositório | `reservation-service` e `billing-service` (Panache repository em `adapter/out/persistence`) |
| **JPA tradicional** | padrão do mercado, mais ceremony | exploração, não padrão do projeto |

Mais: **REST Data** (endpoint automático sobre a entidade) e **NoSQL** com Panache.

## 2. A decisão que este projeto toma

🔀 **Divergência central.** O livro usa Panache **na entidade de domínio** (`Reservation`
é entidade Panache e ao mesmo tempo modelo de domínio). Aqui isso é proibido:

```text
Domain Aggregate
     ↓
Application Repository Port      ← interface NOSSA
     ↓
Persistence Adapter             ← Panache/JPA mora AQUI
     ↓
Panache / JPA / MongoDB
```

Portanto, no projeto:

- **nenhuma** entidade de domínio estende `PanacheEntity` ou `PanacheMongoEntity`;
- **nenhum** repositório Panache é injetado em caso de uso — o caso de uso recebe a
  **porta** (`InvoiceRepository`, `VehicleRepository`);
- active record do livro só sobrevive como **exercício de framework** em ponto isolado
  (`rental-service`, MongoDB), e mesmo lá a entidade de persistência é separada do
  agregado.

Motivo declarado: entidade de persistência que também é domínio vaza schema para o
negócio e vira API pública de trégua. Ver
[ddd-tdd-standards.md](../ddd-tdd-standards.md) §5.

## 3. Mapas de datasource no projeto

| Serviço | Engine | Extensão | Dev/test |
|---|---|---|---|
| `inventory-service` | MySQL | `quarkus-hibernate-reactive-panache` | Dev Service |
| `reservation-service` | PostgreSQL | `quarkus-hibernate-reactive-panache` | Dev Service |
| `rental-service` | MongoDB | `quarkus-mongodb-panache` | Dev Service |
| `billing-service` | PostgreSQL | `quarkus-hibernate-reactive-panache` + `quarkus-reactive-pg-client` | Dev Service |

Schema e carga inicial:

```properties
quarkus.hibernate-orm.database.generation=drop-and-create
quarkus.hibernate-orm.sql-load-script=import.sql
```

- `sql-load-script=import.sql` existe **apenas no `inventory-service`**; nenhum outro
  serviço importa `import.sql` no boot.
- 🔀 `drop-and-create` + `import.sql` é decisão **didática**, não de produção. Ela existe
para que o Dev Service suba já populado e o Dev UI funcione. Está documentada como tal em
cada `application.properties`.

## 4. Transações: declarativo × manual (cap. 7.5)

O livro mostra os dois caminhos e é aqui que o projeto mais diverge:

```java
// declarativo: o CDI abre/commita/fecha
@Transactional
void save(Invoice invoice) { ... }

// manual: controle explícito do escopo
QuarkusTransaction.requiringNew().run(() -> ...);
```

🔀 **O projeto usa o caminho declarativo do Hibernate Reactive**, que é o único que
compoe bem com `Uni`:

```java
@WithTransaction
public Uni<Void> process(UUID eventId, Supplier<Uni<Void>> businessEffect) {
    return processedEventStore.tryClaim(eventId)
            .flatMap(claimed -> claimed
                    ? businessEffect.get()
                    : Uni.createFrom().voidItem());
}
```

Fonte: `billing-service/.../messaging/TransactionalInboxProcessor.java`

Por que isso importa: `@WithTransaction` + `Uni` garante que **o commit aconteça quando a
pipeline termina com sucesso**, e o rollback acontece em falha. Com
`QuarkusTransaction` imperativo dentro de `Uni`, o commit pode acontecer **antes** do efeito
completo. Ver [09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md).

## 5. REST Data (cap. 7.4)

Endpoint gerado automaticamente sobre a entidade. O projeto usa **uma** vez, no
`reservation-service`, e registra explicitamente que é **exercício de framework** e
**fronteira administrativa** — não muda a regra de domínio:

> "The reactive REST Data endpoint in Reservation is an explicit framework exercise for the
> Database access chapter. It remains an administrative boundary and does not change the
> domain rule above."
> — [ddd-tdd-standards.md](../ddd-tdd-standards.md) §5

Se um dia alguém criar regra de negócio nesse endpoint, isso viola a regra 9 do
[AGENTS.md](../../AGENTS.md).

## 6. Dados reativos (cap. 7.7)

Hibernate Reactive + Panache:

```java
@WithTransaction
public Uni<Reservation> make(CreateReservation.Command command) {
    return repository.save(reservation).onItem()
            .invoke(saved -> outbox.append(saved.toEvent()));
}
```

O retorno é `Uni`, não entidade. Vantagem: o método é non-blocking, o event loop **não**
fica bloqueado esperando o banco. Detalhe de execução em
[07-programacao-reativa.md](./07-programacao-reativa.md).

## 7. Checklist de estudo

- [ ] Sei comparar active record, repository pattern e JPA tradicional.
- [ ] Sei explicar por que entidade Panache **não** é entidade de domínio aqui.
- [ ] Sei dizer qual serviço usa qual engine e por quê.
- [ ] Sei explicar a diferença entre `@Transactional` e `@WithTransaction` neste projeto.
- [ ] Sei dizer onde o REST Data é usado e por que é exception.
- [ ] Sei dizer por que `drop-and-create` é didático, não operacional.

**Veja também:** [07-programacao-reativa.md](./07-programacao-reativa.md) ·
[10-exemplos-contrarios-ao-dominio.md](./10-exemplos-contrarios-ao-dominio.md) ·
[09-padroes-de-resiliencia-em-messaging.md](./09-padroes-de-resiliencia-em-messaging.md)

---

_Última atualização: 2026-09-26 (cap. 7)._
