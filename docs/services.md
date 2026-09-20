# Serviços — Detalhamento

> **Última atualização:** 2026-09-20 (cap.7 - Database access; split Model/Entity) · **Fonte da verdade:** o código.
>
> Padrão comum a todos: `org.acme.<serviço>.{api, client, model, entity, repository}`,
> porta por env `${NOME:default}` e persistência Panache (Hibernate ORM ou MongoDB) —
> em dev/test os bancos são **Dev Services** (zerados).
>
> **Model/Entity (padrão novo):** `model/*` são POJOs de domínio usados por REST/GraphQL/
> gRPC (Lombok `@Data/@Builder`); `entity/*Entity` são apenas persistência Panache
> (campos públicos, sem Lombok). Cada serviço tem um **`Mapper`** e um **`Repository`**
> (interface falando no model + impl Panache `Panache*Repository`) — a troca entre
> Active Record e Repository pattern não toca no modelo/API.

---

## inventory-service ✅

**Dir:** `inventory-service/` · **Porta:** HTTP 8083 · gRPC 9000

Central do catálogo. Oferece GraphQL (server, code-first) e gRPC (servidor). Sem REST.

**Dependências:** `quarkus-grpc`, `quarkus-smallrye-graphql`, `quarkus-rest-jackson`,
`quarkus-smallrye-graphql-dev`, `quarkus-arc`, Lombok.

### GraphQL (`api/GraphQLInventoryService.java`) — UI em `/graphql`

| Operação | Tipo | Descrição |
|---|---|---|
| `allCars` | Query | Lista com busca (`search`), filtro (`filter{manufacturer,model,plate,status}`), sort (`ID/PLATE_NUMBER/MANUFACTURER/MODEL`), order (`ASC/DESC`), paginação offset/limit |
| `allCarsPage` | Query | Paginado com metadados: `items`, `total`, `offset`, `limit`, `hasNextPage` |
| `findCar(plate)` | Query | Busca por placa (inclui baixados); erro GraphQL se não existir |
| `register(car)` | Mutation | Cadastra; id atribuído pelo repositório; status vira `AVAILABLE` se omitido |
| `remove(plate)` | Mutation | **Baixa (descomissiona)** por placa; retorna `boolean` |
| `fullDescription` | FieldResolver (`@Source`) | Campo virtual `manufacturer + " " + model` |

Modelo `Car`: `id` (ID!), `manufacturer`, `model`, `licensePlateNumber` → exposto como
`plateNumber`; e atributos do domínio **opcionais**: `status` (enum `CarStatus`), categoria
(`Category`), câmbio (`Transmission`), combustível (`FuelType`), `year`, `color`, `seats`,
`dailyRate`. É POJO puro (`model/Car`, Lombok) **com as anotações GraphQL** — o GraphQL fica
acoplado ao modelo, nunca à entidade de persistência.

> **Oferta:** `allCars`/`allCarsPage` **excluem `DECOMMISSIONED` por padrão**; informe
> `filter.status` para consulta explícita. `findCar` é inclusivo (admin).
>
> ⚠️ **Quirk pré-existente do `register`:** o input reusa o próprio `Car` (model) e o
> `id` (e `year`/`seats`) viram obrigatórios no schema GraphQL (`NonNull`/primitivo) —
> mas o banco é `IDENTITY` e rejeita `id` explícito → "System error". Corrigir com um
> **`CarInput` dedicado** (sem `id`) é candidato a cap.8.

### gRPC (`grpc/GrpcInventoryService.java`) — contrato em `inventory-proto`

| Método | Kind | Descrição |
|---|---|---|
| `add` | **Bidirecional stream** | Recebe `InsertCarRequest` (campos novos opcionais: color/year/category/transmission/fuel_type/seats/daily_rate) → persiste → responde `CarResponse` (com `status` e os demais campos) |
| `remove` | Unary | **Descomissiona** (soft delete) por placa; `CarResponse` vazio se não achar |

Reflection de serviço **sempre ligada** (`quarkus.grpc.server.enable-reflection-service=true`).

### Camada de domínio e repositório (cap.7.2, split Model/Entity)

- **Façade de domínio `domain/CarInventoryService`** (`@ApplicationScoped`): o **único
  core** compartilhado entre GraphQL e gRPC. Regras: `register` normaliza `status`
  para `AVAILABLE`; `decommission` faz **soft delete** (marca `DECOMMISSIONED`, nunca
  apaga a linha — reservas de outro serviço referenciam o id, consistência eventual).
  Transações ficam no façade (`@Transactional`), não nos adapters.
- `model/Car` = **POJO de domínio** (Lombok + anotações GraphQL, sem JPA);
  `entity/CarEntity` (`@Entity @Table(name="car")`) = **só persistência** sobre MySQL
  (`quarkus-jdbc-mysql` + `quarkus-hibernate-orm-panache`), campos públicos,
  enums via `@Enumerated(STRING)`.
- `CarMapper` (estático) converte `Car ↔ CarEntity` com o builder do Lombok.
- **Seam do repositório:** `repository/CarRepository` é uma **interface** falando no
  model (`all`, `findByLicensePlateNumberOptional`, `save` — insert **e** update via
  `merge`); a implementação default é `PanacheCarRepository` (`implements CarRepository,
  PanacheRepository<CarEntity>`). Trocando a impl (Active Record ↔ Repository),
  GraphQL/gRPC não mudam. (**não existe mais hard delete** no contrato).
- Dev/test: Dev Services (MySQL zerado); `%prod`/`%docker` apontam para `inventory-mysql`
  no compose. `import.sql` pré-popula veículos com os atributos novos (`drop-and-create` no boot).

---

## reservation-service ✅

**Dir:** `reservation-service/` · **Porta:** HTTP 8081 · **Teste:** 8181

Orquestra reservas, disponibilidade e consultas de inventário. Consome inventory
(GraphQL) e rental (REST).

**Dependências:** `quarkus-rest-jackson`, `quarkus-rest-client-jackson`,
`quarkus-smallrye-openapi`, `quarkus-smallrye-graphql-client`, `quarkus-oidc`, `quarkus-arc`,
Lombok. Teste: `quarkus-junit`, `rest-assured`, `quarkus-junit-mockito`.

### REST (`api/ReservationResource.java`) — Swagger UI em `/q/swagger-ui`

| Método | Path | Descrição |
|---|---|---|
| POST | `/reservations` | Cria reserva (`make`). Grava `userId` do principal autenticado (`anonymous` sem login). Se `startDay == hoje`, dispara o aluguel via REST p/ rental |
| GET | `/reservations/availability?startDate&endDate` | Disponíveis = inventário − carros c/ reserva sobreposta (cliente GraphQL tipado) |
| GET | `/reservations/availability/dynamic?...&fields` | Mesmo, via cliente **dinâmico** c/ projeção de campos |
| GET | `/reservations/inventory?offset&limit&fields&q&manufacturer&model&plate&sort&order` | Página de carros (projeção + filtros) |
| GET | `/reservations/inventory/pages` (idem) | Mesma consulta, mas com `CarPage` (total/hasNextPage) |
| GET | `/reservations/all` | (cap.6.2.1) Reservas do usuário logado (filtra por `userId`); sem login devolve todas |

### Segurança (cap.6.2.1)

- `quarkus.oidc.application-type=service` — valida `Authorization: Bearer` (tokens do
  Keycloak **compartilhado** com o users-service em dev). **Não** exige login
  (`security context` vazia = acesso anônimo permitido).
- `SecurityContext` injetado por campo (misto com construtor) para ler
  `getUserPrincipal().getName()`.
- Modelo `Reservation` ganhou o campo `userId` (quem fez a reserva).

### Clientes (`client/`)

| Cliente | Tipo | Uso |
|---|---|---|
| `GraphQLInventoryClient` | Tipado `@GraphQLClientApi(configKey="inventory")`, `allCars()` | availability |
| `InventoryClient<T>` | Interface genérica com defaults (`all`, `page`, `fields`, `execute`) | base p/ cliente dinâmico |
| `DynamicInventoryClient` | `DynamicGraphQLClient`, monta `document`/`field`/`inputObject` | availability/dynamic, inventory, inventory/pages |
| `RentalClient` | `@RestClient` `POST /rental/start/{userId}/{reservationId}` | aluguel imediato |

### Persistência (cap.7.1/7.7, split Model/Entity)

- `model/Reservation` = **POJO de domínio** (Lombok `@Data/@Builder`; `isReserved` fica
  no modelo); `entity/ReservationEntity extends PanacheEntity` = **só persistência**
  (campos públicos, sem Lombok) sobre **Hibernate Reactive + PostgreSQL**
  (`quarkus-hibernate-reactive-panache`/`reactive-pg-client`).
- `ReservationMapper` (estático) converte `Reservation ↔ ReservationEntity`.
- **Seam do repositório:** `repository/ReservationRepository` (interface reativa:
  `all`, `save`) + `PanacheReservationRepository` (`implements ReservationRepository,
  PanacheRepository<ReservationEntity>` com `@WithSession` para o session reativo —
  os statics da entidade abrem sessão on demand; os métodos do repositório não).
- Endpoints que tocam o banco retornam `Uni` + `@WithTransaction`
  (`make` persiste reativamente; `availability` combina inventário + reservas com `Uni.combine`).
- **REST Data reativo** (cap.7.4/7.7): `rest/ReservationCrudResource extends
  PanacheEntityResource<ReservationEntity, Long>` gera CRUD em
  **`/reservations/admin/reservation`** (prefixo no path, pois o gateway encaminha sem
  strip) **sobre a entidade** — endpoint interno de admin; a API pública expõe só o model.
- Dev/test: Dev Services (Postgres zerado); `%prod`/`%docker` → `reservation-postgres`
  no compose. `ReservationPersistenceTest` cobre CRUD + sobreposição (white-box na entidade).

### Config relevantes

- `quarkus.oidc.application-type=service` (cap.6.2.1); produção: `%prod.*` →
  `localhost:7777/realms/car-rental`, client `reservation-service` (`%docker.*` →
  `keycloak:8080`).
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

### Persistência (cap.7.6, split Model/Entity)

- `model/Rental` = **POJO de domínio** (Lombok; `id` serializado como **hex do
  ObjectId** — compatível com o DTO do reservation); `entity/RentalEntity extends
  PanacheMongoEntity` = **só persistência** (**MongoDB** via `quarkus-mongodb-panache`).
- `RentalMapper` (estático) converte `Rental ↔ RentalEntity` (ObjectId ↔ hex).
- **Seam do repositório:** `repository/RentalRepository` (interface: `start`, `end`,
  `list`, `listActive`, `findByUserAndReservationIdsOptional`) + `PanacheRentalRepository`
  (`implements RentalRepository, PanacheMongoRepository<RentalEntity>`). As consultas
  (`findByUserAndReservationIdsOptional`, `listActive`) **saíram da entidade** para o
  repositório.
- Dev/test: Dev Services (Mongo zerado); `%prod`/`%docker` → `rental-mongo` no compose.

> ⚠️ Estado parcial: só o fluxo de "start" existe; fluxos de devolução/pagamento etc.
> virão nos caps. 7-9.

---

## users-service ✅

**Dir:** `users-service/` · **Porta:** HTTP 8080

**Interface web do usuário** (servidor de páginas Qute) + **autenticação
OIDC/Keycloak**. Atua como BFF: o frontend HTMX chama endpoints locais que consultam o
reservation-service **propagando o token** do usuário logado (cap.6).

**Dependências:** `quarkus-qute`, `quarkus-rest-qute`, `quarkus-rest-client-jackson`,
`quarkus-oidc`, `quarkus-rest-client-oidc-token-propagation`, `quarkus-smallrye-openapi`,
`quarkus-arc`, Lombok.

### Recursos (cap.6)

| Método | Path | Descrição |
|---|---|---|
| GET | `/` | Página de gerenciamento (`templates/ReservationsTemplates/index.html`): header com usuário + logout, lista de reservas e carros disponíveis |
| GET | `/get` | Fragmento HTML: tabela de reservas do usuário (via `client.allReservations()`) |
| GET | `/available?startDate&endDate` | Fragmento HTML: carros disponíveis no período (via `client.availability()`) |
| POST | `/reserve` (startDate, endDate, carId) | Cria reserva (via `client.make()`); responde lista atualizada + header `HX-Trigger-After-Swap` (recarrega carros) |
| GET | `/whoami` | `WhoAmIResource` — mostra usuário autenticado (ou `anonymous`) + link de logout |
| GET | `/logout` | Logout OIDC (`quarkus.oidc.logout.path`) |

Templates (Qute **checked**, namespaces extraídas): `templates/ReservationsTemplates/{index,
availablecars,listofreservations}.html` + `templates/WhoAmITemplates/whoami.html`. **Estilo:
Bootstrap 5.3 via WebJars** (`/webjars/bootstrap/css/bootstrap.min.css`) **+ HTMX local**
(WebJars npm, `/webjars/htmx.org/dist/htmx.min.js`, via `quarkus-web-dependency-locator`) —
sem CDN/internet no runtime. As atualizações da página são **fragmentos HTML**, sem
JavaScript.

O usuário-service é organizado em camadas (espelhando o reservation-service):
`web` (resources que servem páginas), `client` (REST client), `model`, `templates`
(namespaces `@CheckedTemplate`) e `security` — o bean request-scoped **`CurrentUser`**
(baseado em `SecurityIdentity`) centraliza o usuário autenticado (`getUserId()`/`getDisplayName()`).

### Segurança OIDC

- `quarkus.oidc.application-type=web_app` (Authorization Code Flow) + **todas as rotas**
  exigem login (`all-resources.paths=/*` → `policy=authenticated`).
- **Dev:** Keycloak Dev Services (usuários `alice`/`bob`, senha = usuário),
  compartilhado com o reservation-service quando ambos estão em dev.
- **Prod:** realm `car-rental` (client `users-service`) — ver `deployment.md`.

### Cliente REST (`ReservationsClient`)

- `@RegisterRestClient(configKey="reservations")` + **`@AccessToken`** → propaga o ID
  token do usuário via `Authorization: Bearer ...` ao reservation-service.
  Métodos: `allReservations()` (GET `/reservations/all`), `make()` (POST
  `/reservations`), `availability(start, end)` (GET `/reservations/availability`).
- URL via `quarkus.rest-client.reservations.url` (`%docker` →
  `http://reservation-service:8081`).
- ⚠️ No Quarkus **3.39** a anotação `@AccessToken` muda de pacote:
  `io.quarkus.oidc.token.propagation` → **`io.quarkus.oidc.token.propagation.common`**
  (extensão relocada).

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