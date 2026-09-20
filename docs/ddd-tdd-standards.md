# DDD + TDD Standards — ACME Car Rental

> **Status:** repository-wide target architecture.
> **Java:** 21.
> **Quarkus:** 3.39.3.
> **Purpose:** provide one consistent architectural language across every module while preserving the real role of each module.

## 1. Architectural intent

This repository is a learning laboratory for:

- Domain-Driven Design
- Test-Driven Development
- Quarkus development
- reactive programming with Mutiny/Vert.x
- distributed systems and service communication
- persistence, security, messaging and native execution

The architecture must emerge from behavior and boundaries.

We do **not** want a cosmetic transformation from:

`model / service / repository / api`

to another set of folders that merely has different names.

The target is:

`Domain -> Application -> Ports -> Adapters`

with DDD boundaries defined by business meaning.

## 2. Bounded contexts and module profiles

| Module | Role | DDD profile |
|---|---|---|
| `inventory-service` | owns vehicle/fleet inventory | full business service |
| `reservation-service` | owns reservations | full business service |
| `rental-service` | owns rental lifecycle | full business service |
| `billing-service` | owns future billing/payment concepts | full business service when activated |
| `users-service` | authenticated web UI/BFF | application + adapters; domain only when real business rules exist |
| `inventory-cli` | administrative client | inbound CLI adapter + outbound gRPC adapter |
| `inventory-proto` | wire contract | contract-only module; no DDD layers |

### Business-service blueprint

A business service should converge toward:

```
src/main/java/org/acme/<context>/
├── domain/
│   ├── model/
│   ├── service/          # only real domain services
│   ├── event/            # domain events, when needed
│   └── exception/
├── application/
│   ├── usecase/
│   └── port/
│       └── out/
└── adapter/
    ├── in/
    │   ├── rest/
    │   ├── graphql/
    │   └── grpc/
    └── out/
        ├── persistence/
        ├── rest/
        ├── graphql/
        ├── grpc/
        └── messaging/
```

Only create the subpackages actually used by the bounded context. Do not create empty layers for symmetry.

### BFF blueprint — users-service

```
src/main/java/org/acme/users/
├── application/
│   ├── usecase/
│   └── port/out/
└── adapter/
    ├── in/web/
    ├── out/reservation/
    ├── security/
    └── templates/
```

The BFF must not contain a second copy of the reservation or inventory domain. Wire models belong to the relevant adapter.

### CLI blueprint

```
src/main/java/org/acme/inventory/client/
├── adapter/in/cli/
└── adapter/out/grpc/
```

The CLI is not a bounded context. It should remain a thin delivery client unless command-specific business behavior actually appears.

### Contract module

`inventory-proto` contains only protocol/schema artifacts and their build configuration. Generated classes are consumer artifacts, not hand-written domain models.

## 3. DDD tactical rules

### 3.1 Aggregate roots

Use an aggregate root when a consistency boundary exists. Current candidates:

- Inventory: `Vehicle`
- Reservation: `Reservation`
- Rental: `Rental`
- Billing: define only when billing behavior is implemented

Do not create an aggregate merely because a class is called an Entity.

### 3.2 Entities

Entities own identity and behavior relevant to their lifecycle.

Avoid public setters on domain entities.

Prefer intent-revealing operations such as:

```java
reservation.confirm();
reservation.cancel();
vehicle.decommission();
rental.start();
rental.finish();
```

The exact method names must follow the ubiquitous language selected for the context.

### 3.3 Value objects

Use value objects for concepts with their own invariants, for example:

- `RentalPeriod`
- `LicensePlate`
- `Money`
- `CustomerId`
- `VehicleId`

A value object should be immutable and validated at construction.

Do not introduce value objects for primitives that have no meaningful invariant.

### 3.4 Domain services

Use a domain service only when behavior is genuinely domain logic and does not naturally belong to one aggregate/value object.

Do not use a domain service as a dumping ground for orchestration.

### 3.5 Domain purity

The domain must not import:

- `jakarta.*` framework annotations
- `io.quarkus.*`
- `io.smallrye.mutiny.*`
- REST/GraphQL/gRPC types
- Panache/JPA/Mongo types
- HTTP exceptions
- transport DTOs

Time-sensitive rules should receive a clock/time value from the outside rather than calling `LocalDate.now()` deep inside domain logic.

## 4. Application layer

The application layer expresses use cases.

Examples:

- `CreateReservation`
- `FindAvailableVehicles`
- `DecommissionVehicle`
- `StartRental`
- `EndRental`

Application code:

- coordinates aggregates;
- invokes output ports;
- translates domain failures into application outcomes;
- manages transaction/use-case boundaries;
- composes asynchronous calls when required.

Application code must not contain transport-specific concerns.

`Uni` and `Multi` are allowed here when the use case genuinely performs asynchronous I/O.

## 5. Ports and adapters

### Inbound ports

Use cases may be exposed through REST, GraphQL, gRPC or CLI.

Transport DTOs stay in the inbound adapter.

### Outbound ports

External systems are represented by application-facing interfaces.

Example:

```java
public interface RentalGateway {
    Uni<RentalResult> start(RentalStartRequest request);
}
```

The MicroProfile REST client, HTTP details and serialization types belong to the adapter implementation.

### Persistence

Persistence entities are infrastructure objects:

```
domain aggregate
      ↓
application output port
      ↓
persistence adapter
      ↓
Panache/JPA/Mongo
```

Never move Panache/JPA annotations into the domain just to make persistence easier.

## 5.1 Quarkus REST Data exception

O `quarkus-hibernate-reactive-rest-data-panache` pode expor diretamente uma `PanacheEntityResource` em um endpoint administrativo/interno. Essa é uma exceção deliberada para demonstrar o recurso do capítulo de Database access; ela não autoriza transportar entidades de persistência pela API pública.

## 6. Context boundaries

No bounded context may import another context's:

- domain entity;
- persistence entity;
- repository implementation;
- internal package.

Cross-context communication uses:

- REST;
- GraphQL;
- gRPC;
- messaging;

through an explicit adapter and contract.

A shared library is allowed only for truly technical infrastructure or immutable protocol contracts. It must not become a shared domain model.

## 7. TDD standard

Every behavior follows:

```
Specification
   ↓
RED — failing test
   ↓
GREEN — minimum implementation
   ↓
REFACTOR — improve design while tests stay green
```

### Test levels

**Domain tests**
- pure JUnit;
- no Quarkus startup;
- no database;
- fast;
- focus on invariants and behavior.

**Application tests**
- pure JVM tests;
- mocks/fakes for output ports;
- focus on orchestration and use-case outcomes.

**Adapter tests**
- `@QuarkusTest` only when the framework/container is part of the behavior;
- REST: RestAssured;
- GraphQL/gRPC: protocol-level tests/contracts;
- security: authenticated/anonymous behavior;
- persistence: Dev Services/integration tests.

**Integration/native**
- `@QuarkusIntegrationTest` for artifact/runtime verification;
- only for behavior that benefits from running the packaged artifact.

Do not use `@QuarkusTest` for a rule that can be proved by a plain unit test.

### Test naming

Test names describe behavior:

`shouldRejectReservationWhenVehicleIsAlreadyReserved`

rather than implementation:

`testReservationServiceMethodX`

### Test smell rules

Avoid:
- tests coupled to private implementation details;
- giant end-to-end tests for simple domain rules;
- mocks everywhere;
- testing framework wiring through domain tests;
- duplicated test fixtures with no shared meaning.

## 8. Reactive standard

Reactive programming is an execution model, not a return-type decoration.

Rules:

- non-blocking I/O remains on the reactive path;
- blocking operations must be isolated explicitly;
- `Uni` means asynchronous completion of one result, not automatically a new thread;
- `Multi` models streams;
- concurrency limits and backpressure are separate concerns;
- downstream capacity, timeouts, retries, cancellation and idempotency must be considered;
- do not add `@Blocking` blindly; understand why the operation blocks;
- never hide a blocking database/client call behind a `Uni`.

The domain remains synchronous/pure even when the application and adapters are reactive.

## 9. Quarkus/book alignment

The project must retain executable evidence for the concepts already studied and add later chapters deliberately.

| Book area | Repository evidence |
|---|---|
| Dev mode/productivity | continuous testing and dev tooling |
| Communications | REST, GraphQL, gRPC |
| Testing | unit/Quarkus/integration/profile testing |
| Security | OIDC + Keycloak + token propagation |
| Database | relational, reactive and Mongo persistence |
| Reactive | Mutiny, reactive clients/persistence, event-loop-safe code |
| Messaging | Reactive Messaging + explicit event contracts |

For version-sensitive Quarkus APIs/configuration, verify against the pinned `3.39.3` version and official docs before coding.

## 10. Documentation requirements

A meaningful architectural change updates the relevant source of truth:

- `docs/architecture.md` — boundaries/decisions
- `docs/services.md` — service responsibilities
- `docs/testing.md` — testing strategy
- `docs/contracts.md` — wire/event contracts
- `docs/roadmap.md` — book/chapter progress

Every ADR must explain context, decision, consequences and alternatives considered.

## 11. Cross-service consistency checklist

Before merging a new pattern into one service, ask:

1. Is this a domain concept or an infrastructure concern?
2. Would the same feature be implemented differently in another business service?
3. Does the difference come from the bounded context, or from personal preference?
4. Does the pattern preserve the domain/application/adapter dependency direction?
5. Is there a test proving the behavior?
6. Does the pattern preserve the Quarkus book learning objective?

A local exception must be documented with a reason.

## 12. Target evolution

The first migration targets are:

1. reservation-service
2. inventory-service
3. rental-service
4. users-service BFF
5. billing-service as it becomes real

The goal is not a large-bang rewrite. Migrate behavior-by-behavior using TDD, preserving working functionality and making the Git history show the design evolution.
