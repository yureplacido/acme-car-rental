# ADR — Reactive Inventory Bulk Import

## Status

Accepted / implementada — **escopo: camada de aplicação** (2026-10-21). O núcleo especificado
aqui existe em código e tem evidência executável: `BulkRegisterVehicles`, a porta reativa
`Uni<Vehicle> save(...)`, o gRPC `add(stream) returns (stream)` com `merge(maxConcurrency)`
configurável por `inventory.bulk.max-concurrency`, e os testes de concorrência, cancelamento,
timeout e retry.

Dois compromissos desta ADR **não** têm evidência executável: os quatro cenários de teste de
adapter (§Adapter/integration tests — 0 de 4) e o wiring CDI da propriedade de concorrência.
Ver a seção **Estado da decisão** no fim deste documento para o que ficou fora do primeiro slice.

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
- in-flight sibling operations are cancelled, so a failed stream stops doing work;
- already completed items remain persisted.

Cancellation has two distinct paths, and both are observable: the consumer may cancel the
stream, or a failure may cancel the siblings that were in flight. Who cancels changes the
scenario, so each path carries its own test.

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

The scenarios below are the ones this ADR specified. The table maps each one to the test that
implements it today: the names diverged during implementation — the code names describe the
behaviour more precisely — and renaming the code back to the original wording would be a
degradation, so the mapping lives here.

| Scenario specified by this ADR | Executable evidence |
|---|---|
| shouldRegisterAllCommandsFromTheStream | `BulkRegisterVehiclesTest.shouldRegisterAllCommandsFromStream` |
| shouldPreserveTheConfiguredMaximumConcurrency | `BulkRegisterVehiclesTest.shouldNeverExceedConfiguredConcurrency` (`maxInFlight == 2` with limit 2) |
| shouldPropagatePersistenceFailure | `BulkRegisterVehiclesFailurePolicyTest.shouldPropagatePersistenceFailureWithoutLosingAlreadyPersistedItems` |
| shouldStopOnCancellation | `BulkRegisterVehiclesCancellationTest.shouldPropagateCancellationToActiveOperationsAndStopNewOnes` |
| shouldNotMaterializeTheEntireInputStreamBeforeProcessing | `BulkRegisterVehiclesMaterializationTest.shouldNotMaterializeTheEntireInputStreamBeforeProcessing` |

A counterpoint case guards the negative test: `shouldConsumeTheWholeSourceWhenDemandIsUnbounded`
proves the source is fully consumed under unbounded demand, so a pipeline that simply consumed
nothing cannot pass the test above by accident.

### Domain tests

No new domain tests are required unless implementation reveals a missing Vehicle invariant.

Existing Vehicle tests remain authoritative for registration behavior.

### Adapter/integration tests

- gRPC streaming request produces a streaming response;
- protobuf DTOs map to application commands;
- reactive persistence is used without blocking the event loop;
- end-to-end bulk import completes with the expected number of persisted vehicles.

**None of these four has executable evidence yet (0 de 4).** The repository has no gRPC adapter
test at all, and `inventory-cli` has no test source tree. The status of this ADR is therefore
scoped to the application layer — see **Estado da decisão**.

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

**Fulfilled in this slice:** `docs/architecture.md` (reactive ingestion flow), `docs/services.md`
(the `BulkRegisterVehicles` use case), `docs/testing.md` (concurrency/backpressure evidence) and
`docs/ddd-tdd-standards.md` §8 — the last one because the rule "claiming backpressure requires
demand, production and a counterpoint" is a repository-wide convention, not a one-off for this
ADR.

## Open questions

None blocking for the first implementation slice.

The initial concurrency value, timeout values and future retry strategy are implementation policies and can be refined after measurements.

---

## Estado da decisão (2026-10-21)

**Implementado com evidência executável, no escopo da camada de aplicação.** Esta ADR deixou
de ser `Proposed`: o código que ela especifica existe e é testado. O que segue é o compromisso e
onde a prova está — e, depois, o que continua sem prova.

| Compromisso da ADR | Evidência |
|---|---|
| `BulkRegisterVehicles` com `Multi<Command>` → `Multi<Vehicle>` | `BulkRegisterVehiclesTest.shouldRegisterAllCommandsFromStream` |
| Concorrência limitada e configurável (`N` fora do domínio) | `inventory.bulk.max-concurrency` (default 4) + `BulkRegisterVehiclesTest.shouldNeverExceedConfiguredConcurrency` (`maxInFlight == 2`) |
| Cancelamento propagado e sem trabalho novo | `BulkRegisterVehiclesCancellationTest` (2 em voo canceladas, nenhuma terceira inicia) |
| Backpressure: o pipeline **não materializa** o stream inteiro | `BulkRegisterVehiclesMaterializationTest.shouldNotMaterializeTheEntireInputStreamBeforeProcessing` (identidade `produced <= requested <= maxConcurrency`; sem demanda, `requested=4` e `produced=4` numa fonte de 100) + o contraponto `shouldConsumeTheWholeSourceWhenDemandIsUnbounded` |
| Backpressure: trabalho em voo limitado | `BulkRegisterVehiclesBackpressureTest` (demanda 0 → exatamente 2 requests ao upstream) |
| Política fail-fast: a falha propaga e o já persistido fica | `BulkRegisterVehiclesFailurePolicyTest.shouldPropagatePersistenceFailureWithoutLosingAlreadyPersistedItems` |
| Política fail-fast: a falha cancela as irmãs em voo | `BulkRegisterVehiclesFailurePolicyTest.shouldCancelSiblingsInFlightWhenOneItemFails` (comportamento medido do `merge`, não presumido) |
| Porta reativa `Uni<Vehicle> save(Vehicle)` | `BulkRegisterVehicles` + `VehicleRepository` reativo |
| gRPC como fronteira, sem decidir política | `GrpcInventoryResource.add` só traduz e delega |
| Falha de item não corrompe os demais | o item que falhou não é gravado e os concluídos ficam: `saved == 2` com a falha no 3º item (`BulkRegisterVehiclesFailurePolicyTest`) |

**Fora do primeiro slice, e o limite é deliberado:**

1. **A demanda não atravessa a fronteira gRPC.** O que está provado é o *prefetch do `Merge`* e a
   ausência de materialização — no `use case`, não no transporte. O gRPC **não** encaminha a
   demanda do cliente para o repositório, então um cliente real não consegue hoje dizer "não me
   mande ainda". É limite do adapter de transporte, e não do use case: a distinção importa porque
   esta própria ADR proíbe tratar limite de concorrência como backpressure.
2. **Operações de query** continuam a migração deliberada prevista em §Reactive output port —
   não foram convertidas neste slice.
3. **Retry e DLQ por item** não entram aqui, por decisão explícita da §Failure policy: pertencem
   ao slice de Messaging/Resiliência (a política de retry do reservation é a ADR 002).

### O que ainda não tem evidência executável

Declarar a ADR como implementada só é honesto se o que falta também estiver escrito. Estas três
linhas são **compromissos assumidos sem prova**, não comportamento escondido:

| Compromisso | Situação |
|---|---|
| §Adapter/integration tests — gRPC streaming, mapeamento protobuf, persistência reativa, bulk ponta a ponta | **0 de 4.** Não existe teste de adapter gRPC no repositório, e o `inventory-cli` não tem árvore de teste. É o contrato de ingestão do sistema, e uma mudança no `.proto` ou no mapeamento `InsertCarRequest → Command` hoje passaria verde. |
| O wiring CDI de `inventory.bulk.max-concurrency` para o construtor do use case | **Sem teste.** A evidência prova que o use case respeita o `N` que recebe — e que ele é de fora do domínio — mas quem injeta o `N` (o container) não é exercitado. |
| Persistência reativa real (Hibernate Reactive/Panache não bloqueante) | **Sem teste neste slice.** O caminho sob teste usa repositório fake; a stack reativa do inventory é coberta pelo cap. 7. |

**Versões:** a evidência desta ADR usa `merge`, `AssertSubscriber` e `UniAssertSubscriber` do
**Mutiny 3.3.0** — a versão que o `quarkus-bom` 3.39.3 resolve para este repositório. Nenhum
`pom.xml` fixa a versão do Mutiny; ela vem do BOM, e por isso é version-sensitive.

---
_Last updated: 2026-10-21_
