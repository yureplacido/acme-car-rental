# DDD + TDD Standards — ACME Car Rental

> **Status:** repository-wide target architecture.
> **Java:** 21.
> **Quarkus:** 3.39.3.
> **Purpose:** provide one consistent architectural language across every module while preserving the real role of each module.

## 1. Architectural intent

This repository is a learning laboratory for Domain-Driven Design, Test-Driven Development, Quarkus, reactive programming, distributed systems, persistence, security and messaging.

The architecture must emerge from behavior and boundaries, not from a cosmetic package rearrangement.

Target dependency direction:

```
Adapter In
   ↓
Application
   ↓
Domain
   ↑
Application Ports Out
   ↑
Adapter Out
```

## 2. Bounded contexts and module profiles

| Module | Role | DDD profile |
|---|---|---|
| `inventory-service` | owns vehicle/fleet inventory | full business service |
| `reservation-service` | owns reservations | full business service |
| `rental-service` | owns rental lifecycle | full business service |
| `billing-service` | owns billing/payment foundation | full business service |
| `users-service` | authenticated web UI/BFF | application + adapters; domain only when real business rules exist |
| `inventory-cli` | administrative client | inbound CLI adapter + outbound gRPC adapter |
| `inventory-proto` | wire contract | contract-only module |

### Business-service blueprint

```
org.acme.<context>/
├── domain/
│   ├── model/
│   ├── service/
│   ├── event/
│   └── exception/
├── application/
│   ├── usecase/
│   ├── query/
│   └── port/out/
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

Only create packages that have a real responsibility. Do not create empty layers for symmetry.

### Special profiles

**users-service** is a BFF. It owns browser/application concerns, not copies of other bounded contexts.

**inventory-cli** is a client, not a bounded context.

**inventory-proto** is a contract module and contains no domain.

## 3. Domain design rules

### 3.1 Aggregates

An aggregate is a consistency boundary, not a synonym for database entity.

Current aggregate roots:

- Inventory: `Vehicle`, `MaintenanceOrder`
- Reservation: `Reservation`
- Rental: `Rental`
- Billing: `Invoice`

New aggregates require a domain reason: identity, lifecycle or invariant boundary.

### 3.2 Entities

Entities own identity and behavior relevant to their lifecycle.

Avoid public setters in domain entities. Prefer intention-revealing commands such as:

```java
reservation.confirm();
reservation.cancel();
vehicle.decommission();
vehicle.recordOdometer(reading);
maintenance.complete();
rental.finish(date);
invoice.markPaid();
```

### 3.3 Value objects

Use immutable value objects when a concept has identity semantics or invariants of its own.

Current examples:

- Inventory: `VehicleId`, `LicensePlate`, `VehicleSpecifications`, `VehicleLocation`, `VehicleDailyRate`, `OdometerReading`
- Reservation: `ReservationId`, `CustomerId`, `VehicleId`, `RentalPeriod`
- Rental: `RentalId`, `ReservationId`, `CustomerId`
- Billing: `InvoiceId`, `Money`

Do not wrap primitives without a domain reason.

### 3.4 Domain purity

Domain code must not import framework, transport or persistence types:

- `jakarta.*`
- `io.quarkus.*`
- `io.smallrye.mutiny.*`
- REST/GraphQL/gRPC types
- Panache/JPA/Mongo types
- HTTP exceptions
- transport DTOs

Time-sensitive domain rules receive the relevant date/time from the application layer rather than calling `now()` directly.

### 3.5 Domain services

Use a domain service only for real domain behavior that does not belong naturally to one aggregate/value object.

A domain service is not an application service with a different name.

## 4. Application rules

The application layer expresses use cases and coordinates domain objects and output ports.

Examples:

- `RegisterVehicle`
- `SearchVehicles`
- `CreateReservation`
- `FindAvailableVehicles`
- `StartRental`
- `EndRental`
- `CreateInvoice`

Application commands/queries should use domain concepts rather than transport DTOs.

`Uni`/`Multi` are allowed here only when asynchronous I/O or streaming is part of the actual use case.

### Query responsibility

Filtering, sorting, pagination and application-level selection belong in application query use cases, not transport adapters, unless the logic is purely protocol syntax mapping.

For example:

```
GraphQL input
   ↓
adapter maps input
   ↓
VehicleSearch
   ↓
SearchVehicles
   ↓
VehiclePage
   ↓
adapter maps output
```

This keeps GraphQL/REST/gRPC differences at the edge.

## 5. Ports and adapters

### Inbound adapters

REST, GraphQL, gRPC and CLI adapters translate external protocols into application commands/queries.

Transport DTOs remain inside adapters.

### Outbound ports

External systems are represented by application-facing interfaces.

Example:

```java
public interface RentalGateway {
    Uni<Void> start(String customerId, Long reservationId);
}
```

HTTP client annotations, serialization types and provider-specific behavior stay in the outbound adapter.

### Persistence

```
Domain Aggregate
      ↓
Application Repository Port
      ↓
Persistence Adapter
      ↓
Panache / JPA / MongoDB
```

Persistence entities must never become domain entities by convenience.

### Quarkus REST Data exception

The reactive REST Data endpoint in Reservation is an explicit framework exercise for the Database access chapter. It remains an administrative boundary and does not change the domain rule above.

## 6. Context boundaries

A bounded context must not import another context's:

- domain entity;
- persistence entity;
- repository implementation;
- internal package.

Cross-context communication uses explicit contracts and adapters:

- REST
- GraphQL
- gRPC
- messaging

Do not create a shared domain model merely to avoid mapping.

## 7. TDD

Every behavior follows:

```
Specification
    ↓
RED — failing test
    ↓
GREEN — minimum behavior
    ↓
REFACTOR — design improvement
    ↓
Adapter / integration evidence when needed
```

### Domain tests

Pure JUnit. No Quarkus, database, transport or Mutiny.

### Application tests

Pure JVM. Use fakes/mocks at output ports. Verify orchestration and outcomes.

### Adapter tests

Use `@QuarkusTest` when the framework/protocol is part of the behavior.

### Integration/native

Use `@QuarkusIntegrationTest` for packaged artifact/runtime verification.

A test written only after implementation is not enough to claim TDD.

Test names describe behavior, for example:

`shouldRejectReservationWhenVehicleIsAlreadyReserved`

## 8. Reactive standard

Reactive code must explicitly account for:

- event-loop safety;
- blocking isolation;
- concurrency;
- backpressure;
- failure propagation;
- cancellation;
- timeout;
- retries;
- idempotency;
- downstream capacity.

`Uni` does not create a thread automatically. `Multi` models a stream. Concurrency limiting and backpressure are different controls.

The domain remains synchronous and framework-free even when adapters/application orchestration are reactive.

## 9. Quarkus book alignment

The lab should provide executable evidence for:

| Area | Evidence |
|---|---|
| Dev mode/productivity | dev mode + continuous testing |
| Communications | REST + GraphQL + gRPC |
| Testing | unit + Quarkus + integration/profile testing |
| Security | OIDC + Keycloak + token propagation |
| Database | JPA/Panache + Hibernate Reactive + Mongo |
| Reactive | Mutiny + reactive clients/persistence + event-loop safety |
| Messaging | Reactive Messaging + explicit event contracts |
| Native | packaged/native integration tests |

Version-sensitive Quarkus APIs/configuration must be verified against the pinned `3.39.3` version before coding.

## 10. Documentation

Architectural changes update:

- `docs/domain.md`
- `docs/architecture.md`
- `docs/services.md`
- `docs/testing.md`
- `docs/contracts.md`
- `docs/roadmap.md`

## 11. OpenCode governance

Every feature should pass this sequence:

```
Domain Designer
      ↓
Architecture Guardian
      ↓
DDD Guardian
      ↓
TDD Guardian
      ↓
Quarkus Book Guardian
      ↓
Feature implementation
      ↓
Guardians re-run
```

A new architectural pattern becomes repository standard only after it is documented here.

A context-specific exception is valid only when its reason is documented.

## 12. Migration/evolution

Migration remains incremental at behavior level, but the repository must converge on the same architectural language across all business services.

The special-purpose modules follow their own documented profile.
