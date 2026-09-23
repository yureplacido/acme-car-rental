# ADR — Reactive Inventory Bulk Import

## Status

Proposed

## Context

The Inventory/Fleet bounded context already owns the `Vehicle` aggregate and exposes a bidirectional gRPC streaming contract:

```text
rpc add(stream InsertCarRequest) returns (stream CarResponse)
```

The current implementation receives a `Multi`, but the registration path is synchronous and the persistence adapter is blocking. This does not yet provide useful executable evidence for reactive execution, controlled concurrency or backpressure.

The goal of this feature is to create executable evidence for Quarkus Reactive without leaking Mutiny or transport concerns into the domain.

## Domain decision

No new aggregate is required.

The existing `Vehicle` aggregate remains the consistency boundary for vehicle registration. Registering a vehicle is still a domain behavior represented by `Vehicle.register(...)`.

The new capability is an application workflow: **bulk vehicle import**.

This is an application workflow, not a new domain entity.

## Bounded context

Inventory / Fleet.

Ownership remains entirely inside `inventory-service`.

## Aggregates and value objects

Existing `Vehicle` aggregate and existing value objects remain authoritative:

- `VehicleId`
- `LicensePlate`
- `VehicleSpecifications`
- `VehicleLocation`
- `VehicleDailyRate`
- `OdometerReading`

No transport DTO or persistence entity crosses into the domain.

## Invariants

The bulk flow must preserve the same invariants as single registration:

- a registered vehicle starts as `AVAILABLE`;
- vehicle identity and license plate invariants remain enforced by the domain;
- optional vehicle data is normalized before entering the domain;
- each item is persisted independently unless a future business rule explicitly requires batch atomicity;
- one failed item must not corrupt the domain state of other successfully processed items;
- the domain itself remains synchronous and framework-free.

Reactive concerns are execution policies, not domain invariants.

## Domain events

No domain event is introduced in this slice.

Vehicle registration does not currently have a cross-context event requirement in the lab. Messaging belongs to the later Messaging chapter.

## Application use case

Introduce a dedicated application use case:

```
BulkRegisterVehicles
    input:  Multi<RegisterVehicle.Command>
    output: Multi<Vehicle>
```

The command is an application contract, not a gRPC message.

The use case is responsible for:

1. receiving application commands as a stream;
2. invoking existing Vehicle domain registration behavior;
3. delegating persistence through a reactive output port;
4. applying a bounded concurrency policy;
5. propagating failures according to the defined stream policy;
6. preserving cancellation and downstream demand semantics.

The existing single-item registration behavior remains reusable instead of duplicating domain logic.

## Reactive output port

The Inventory persistence port should evolve to reactive return types where the operation is genuinely asynchronous:

```java
Uni<Vehicle> save(Vehicle vehicle);
```

Query operations will be migrated deliberately rather than introducing overlapping repository abstractions.

The persistence adapter will use Hibernate Reactive/Panache and a reactive SQL client.

The application layer may use `Uni`/`Multi` because asynchronous persistence and streaming are actual use-case requirements.

## Inbound adapter

The existing gRPC `add(Multi<InsertCarRequest>)` endpoint remains the transport boundary.

Responsibilities:

- translate `InsertCarRequest` to `RegisterVehicle.Command`;
- invoke `BulkRegisterVehicles`;
- translate `Vehicle` to `CarResponse`.

The gRPC contract does not change in this slice.

The adapter must not decide concurrency, retry policy, persistence behavior, domain transitions or business filtering.

## Concurrency policy

The first implementation uses explicit bounded concurrency.

```text
Incoming Multi
      |
      | maxConcurrency = N
      v
+-----+-----+-----+-----+
| task | task | task | ... |
+-----+-----+-----+-----+
      |
      v
reactive persistence
```

The value of `N` is an application execution policy and must be configurable/testable, not hidden in the domain.

The initial target is a small fixed default suitable for local development, with a test proving that the configured maximum is never exceeded.

## Backpressure

Backpressure is treated separately from concurrency.

The implementation must provide evidence that the pipeline does not blindly materialize the entire incoming stream in memory.

We will prefer stream operators that preserve demand and bounded in-flight work.

A test will model a producer faster than persistence and verify that in-flight operations remain bounded.

The feature must not claim that a concurrency limit alone is equivalent to backpressure.

## Failure policy

The initial policy is fail-fast for the stream unless a later business requirement introduces per-item error reporting.

When one item fails:

- the corresponding reactive operation fails;
- stream failure is propagated;
- downstream cancellation is respected;
- already completed items remain persisted.

Per-item retry and dead-letter handling belong to later Messaging/Resilience slices.

## Blocking policy

The target architecture is non-blocking along the registration path:

```text
gRPC event loop
      |
      v
application Multi/Uni
      |
      v
Hibernate Reactive
      |
      v
reactive PostgreSQL client
```

`@Blocking` should not remain on the final bulk registration method merely because it exists in the legacy implementation.

If any genuinely blocking operation remains during migration, it must be isolated explicitly and documented as an adapter constraint.

## Test-first scenarios

### Application tests

`BulkRegisterVehiclesTest`

- shouldRegisterAllCommandsFromTheStream
- shouldPreserveTheConfiguredMaximumConcurrency
- shouldPropagatePersistenceFailure
- shouldStopOnCancellation
- shouldNotMaterializeTheEntireInputStreamBeforeProcessing

### Domain tests

No new domain tests are required unless implementation reveals a missing Vehicle invariant.

Existing Vehicle tests remain authoritative for registration behavior.

### Adapter/integration tests

- gRPC streaming request produces a streaming response;
- protobuf DTOs map to application commands;
- reactive persistence is used without blocking the event loop;
- end-to-end bulk import completes with the expected number of persisted vehicles.

## Forbidden shortcuts

Do not:

- put `Uni` or `Multi` in the Vehicle domain;
- create a second Vehicle aggregate;
- create a shared reactive domain model;
- wrap a blocking repository call in `Uni.createFrom().item(...)` and call that a reactive implementation;
- use an unbounded merge and call it backpressure;
- move business rules into the gRPC adapter;
- introduce retry/dead-letter messaging in this slice;
- change the protobuf contract merely to expose internal domain fields unrelated to the use case.

## Quarkus concepts demonstrated

This slice is intended to provide executable evidence for:

- Mutiny `Multi`;
- `Uni` composition;
- gRPC streaming;
- event-loop safety;
- reactive persistence;
- controlled concurrency;
- backpressure;
- cancellation;
- failure propagation.

Quarkus gRPC services execute on the event loop by default, and streaming methods can be represented with Mutiny `Multi`. Blocking implementations must be explicitly isolated. citeturn929268search0turn929268search10

Hibernate Reactive with Panache is the intended persistence model for the non-blocking path; reactive transactions remain inside the reactive pipeline. citeturn929268search5turn929268search6

## Documentation changes

After implementation:

- update `docs/architecture.md` with the reactive Inventory flow;
- update `docs/services.md` with the BulkRegisterVehicles use case;
- update `docs/testing.md` with concurrency/backpressure evidence;
- update `docs/ddd-tdd-standards.md` only if a new repository-wide reactive convention is established;
- mark corresponding Cap. 8 roadmap items complete only after executable evidence exists.

## Open questions

None blocking for the first implementation slice.

The initial concurrency value, timeout values and future retry strategy are implementation policies and can be refined after measurements.
