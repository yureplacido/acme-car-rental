# Serviços — Detalhamento

> **Última atualização:** 2026-09-19 (base cap.1-5) · **Fonte da verdade:** o código.
>
> Padrão comum a todos: `org.acme.<serviço>.{api, client, model, repository}`,
> porta por env `${NOME:default}` e repositório in-memory via `app.repository=memory`.

---

## inventory-service ✅

**Dir:** `inventory-service/` · **Porta:** HTTP 8083 · gRPC 9000

Central do catálogo. Oferece GraphQL (server, code-first) e gRPC (servidor). Sem REST.

**Dependências:** `quarkus-grpc`, `quarkus-smallrye-graphql`, `quarkus-rest-jackson`,
`quarkus-smallrye-graphql-dev`, `quarkus-arc`, Lombok.

### GraphQL (`api/GraphQLInventoryService.java`) — UI em `/graphql`

| Operação | Tipo | Descrição |
|---|---|---|
| `allCars` | Query | Lista com busca (`search`), filtro (`filter{manufacturer,model,plate}`), sort (`ID/PLATE_NUMBER/MANUFACTURER/MODEL`), order (`ASC/DESC`), paginação offset/limit |
| `allCarsPage` | Query | Paginado com metadados: `items`, `total`, `offset`, `limit`, `hasNextPage` |
| `findCar(plate)` | Query | Busca por placa; erro GraphQL se não existir |
| `register(car)` | Mutation | Cadastra; id atribuído pelo repositório |
| `remove(plate)` | Mutation | Remove por placa; retorna `boolean` |
| `fullDescription` | FieldResolver (`@Source`) | Campo virtual `manufacturer + " " + model` |

Modelo `Car`: `id` (ID!), `manufacturer`, `model`, `licensePlateNumber` → exposto como
`plateNumber`. `Person` existe no model (não usado ainda).

### gRPC (`grpc/GrpcInventoryService.java`) — contrato em `inventory-proto`

| Método | Kind | Descrição |
|---|---|---|
| `add` | **Bidirecional stream** | Recebe `InsertCarRequest`→ persiste → responde `CarResponse` |
| `remove` | Unary | Remove por placa; `CarResponse` vazio se não achar |

Reflection de serviço **sempre ligada** (`quarkus.grpc.server.enable-reflection-service=true`).

### Repositório

- Porta: `CarRepository` (`findAll`, `findByPlate`, `save`, `deleteByPlate`).
- Adapter default: `InMemoryCarRepository` (`app.repository=memory`) — seed de **108
  carros** (8 fixos + 100 gerados `GEN0001..0100`).
- 🔜 Migração ch.7: datasource **MySQL** + adapter Panache (comentário no
  `application.properties`).

---

## reservation-service ✅

**Dir:** `reservation-service/` · **Porta:** HTTP 8081 · **Teste:** 8181

Orquestra reservas, disponibilidade e consultas de inventário. Consome inventory
(GraphQL) e rental (REST).

**Dependências:** `quarkus-rest-jackson`, `quarkus-rest-client-jackson`,
`quarkus-smallrye-openapi`, `quarkus-smallrye-graphql-client`, `quarkus-arc`, Lombok.
Teste: `quarkus-junit`, `rest-assured`, `quarkus-junit-mockito`.

### REST (`api/ReservationResource.java`) — Swagger UI em `/q/swagger-ui`

| Método | Path | Descrição |
|---|---|---|
| POST | `/reservations` | Cria reserva (`make`). Se `startDay == hoje`, dispara o aluguel via REST p/ rental |
| GET | `/reservations/availability?startDate&endDate` | Disponíveis = inventário − carros c/ reserva sobreposta (cliente GraphQL tipado) |
| GET | `/reservations/availability/dynamic?...&fields` | Mesmo, via cliente **dinâmico** c/ projeção de campos |
| GET | `/reservations/inventory?offset&limit&fields&q&manufacturer&model&plate&sort&order` | Página de carros (projeção + filtros) |
| GET | `/reservations/inventory/pages` (idem) | Mesma consulta, mas com `CarPage` (total/hasNextPage) |

### Clientes (`client/`)

| Cliente | Tipo | Uso |
|---|---|---|
| `GraphQLInventoryClient` | Tipado `@GraphQLClientApi(configKey="inventory")`, `allCars()` | availability |
| `InventoryClient<T>` | Interface genérica com defaults (`all`, `page`, `fields`, `execute`) | base p/ cliente dinâmico |
| `DynamicInventoryClient` | `DynamicGraphQLClient`, monta `document`/`field`/`inputObject` | availability/dynamic, inventory, inventory/pages |
| `RentalClient` | `@RestClient` `POST /rental/start/{userId}/{reservationId}` | aluguel imediato |

### Repositório

- Porta: `ReservationsRepository` (`findAll`, `save`).
- Adapter default: `InMemoryReservationRepository` — reservas seed (carId 1 e 2) + id
  gerado por `AtomicLong`.
- 🔜 Migração ch.7: **PostgreSQL reativo** (Panache reativo).

### Config relevantes

- `quarkus.rest-client."...RentalClient".uri=${RENTAL_SERVICE_URL:http://localhost:8082}`
  (`%docker` → `http://rental-service:8082`).
- `quarkus.smallrye-graphql-client.inventory.url=${INVENTORY_SERVICE_URL:http://localhost:8083}/graphql`
  (`%docker` → `http://inventory-service:8083/graphql`).
- `quarkus.http.test-port=8181` — evita conflito da instância de teste com o dev.

---

## rental-service ⚠️

**Dir:** `rental-service/` · **Porta:** HTTP 8082

Locação iniciada quando a reserva começa no mesmo dia (chamado pelo reservation).

**Dependências:** `quarkus-rest-jackson`, `quarkus-rest-client-jackson`,
`quarkus-mongodb-panache`, `quarkus-messaging-kafka`, `quarkus-smallrye-openapi`, Lombok.

### REST (`api/RentalResource.java`)

| Método | Path | Descrição |
|---|---|---|
| POST | `/rental/start/{userId}/{reservationId}` | Cria `Rental(userId, reservationId, LocalDate.now())` |

### Persistência

- Porta: `RentalRepository` (`findAll`, `save`).
- Adapter default: `InMemoryRentalRepository`.
- `MongoRentalRepository` — adapter **Mongo/Panache presente** (✅ já usa entidade), mas
  exige MongoDB configurado; hoje comentado/desligado por `app.repository=memory`.
- `persistence/RentalEntity.java` — entidade Panache para quando o Mongo estiver ativo.

> ⚠️ Estado parcial: só o fluxo de "start" existe; fluxos de devolução/pagamento etc.
> virão nos caps. 7-9.

---

## users-service 🚧

**Dir:** `users-service/` · **Porta:** HTTP 8080

**Placeholder** — sem código Java ainda. Estrutura pronta para o **cap.6 (security)**.

**Dependências já declaradas:** `quarkus-qute`, `quarkus-rest-qute`,
`quarkus-rest-client-jackson`, **`quarkus-oidc`**, **`quarkus-rest-client-oidc-token-propagation`**,
`quarkus-smallrye-openapi`, `quarkus-arc`, Lombok.

**Config:** `quarkus.oidc.tenant-enabled=false` (OIDC desligado por enquanto).

🔜 Autenticação/usuario, tokens OIDC e propagação entre serviços.

---

## billing-service 🚧

**Dir:** `billing-service/` · **Porta:** HTTP 8084

**Placeholder** — sem código Java ainda. Estrutura pronta para **mensageria (cap.9)** e
**persistência Mongo**.

**Dependências já declaradas:** `quarkus-rest-jackson`, `quarkus-smallrye-openapi`,
**`quarkus-messaging-kafka`**, **`quarkus-messaging-rabbitmq`**,
**`quarkus-mongodb-panache`**, `quarkus-arc`, Lombok.

🔜 Cobranças/faturas via fila/stream; dados em MongoDB.

---

## inventory-cli ✅

**Dir:** `inventory-cli/` · Sem servidor (gRPC client)

CLI administrativa do inventário via gRPC.

**Config:** `quarkus.grpc.clients.inventory.host=localhost`, `.port=9000`;
`quarkus.grpc.server.port=-1` (não expõe servidor).

**Uso:**

```
java -jar target/quarkus-app/quarkus-run.jar <add|remove> <placa> [<fabricante> <modelo>]
# ex.:
java -jar target/quarkus-app/quarkus-run.jar add KNIGHT Pontiac TransAM
java -jar target/quarkus-app/quarkus-run.jar remove KNIGHT
```

- `add` → streaming **bidirecional** (`Multi.createFrom().item(...)`, respostas via `collect().asList()`).
- `remove` → unário; placa inexistente → "No car found".

---

_Consulte [contracts.md](./contracts.md), [deployment.md](./deployment.md) e [testing.md](./testing.md)._