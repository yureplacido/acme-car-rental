# Domain Model — ACME Car Rental
 
## Purpose
 
The repository is a learning laboratory, but the domain must still be usable as a realistic car-rental platform. DDD is used to model business boundaries, not to create folders for their own sake.
 
## Bounded Context Map
 
```mermaid
flowchart LR
    subgraph EXT["External"]
        Customer["Customer /<br/>Identity (Keycloak)"]
    end

    subgraph CORE["Core Domains"]
        Inventory["Inventory<br/>Vehicle + Maintenance"]
        Reservation["Reservation<br/>Booking Commitment"]
        Rental["Rental<br/>Physical Lifecycle"]
        Billing["Billing /<br/>Payment"]
        Pricing["Pricing<br/>🔜"]
    end

    Customer -->|"CustomerId"| Reservation
    Inventory -->|"VehicleId<br/>VehicleSpecs"| Reservation
    Reservation -->|"ReservationId<br/>RentalPeriod"| Rental
    Inventory -->|"VehicleRegistered<br/>(evento)"| Billing
    Reservation -.->|"ReservationConfirmed<br/>🔜 sem produtor"| Billing
    Rental -.->|"RentalCompleted<br/>🔜 sem produtor"| Billing
    Billing -.->|"InvoiceOpened<br/>🔜 sem consumidor"| Reservation
    Pricing -.->|"PriceQuote 🔜"| Reservation

    classDef core fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;
    classDef external fill:#fce4ec,stroke:#c2185b,stroke-width:2px;
    classDef future fill:#f5f5f5,stroke:#9e9e9e,stroke-width:2px,stroke-dasharray: 5 5;

    class Inventory,Reservation,Rental,Billing core;
    class Customer external;
    class Pricing future;
```

The arrows represent information/contract relationships, not shared object models. A bounded context never imports another context's domain classes. Solid arrows exist in code; dashed arrows are 🔜 planned (see [architecture.md](./architecture.md) for the transport behind each contract).

## 1. Inventory Context
 
### Responsibility
 
Own the fleet, vehicle lifecycle and operational state of the fleet.
 
### Aggregate roots
 
- `Vehicle`
- `MaintenanceOrder`
 
`Vehicle` owns the identity and lifecycle of a fleet vehicle. `MaintenanceOrder` owns a maintenance work lifecycle for a vehicle. They are separate aggregates so maintenance can later evolve into its own persistence, events and workflows without turning Vehicle into a large aggregate.
 
### Vehicle Aggregate Structure
 
```mermaid
classDiagram
    class Vehicle {
        +VehicleId id
        +LicensePlate licensePlate
        +VehicleSpecifications specifications
        +VehicleLocation location
        +VehicleDailyRate dailyRate
        +OdometerReading odometer
        +VehicleCondition condition
        +VehicleStatus status
        +register(plate, specs, location, rate)
        +rehydrate(id, plate, specs, location, status, rate, odometer, condition)
        +decommission()
        +sendToMaintenance()
        +releaseFromMaintenance()
        +relocate(newLocation)
        +recordOdometer(reading)
        +changeCondition(newCondition)
        +canBeOffered()
    }
 
    class VehicleId {
        +Long value
    }

    class LicensePlate {
        +String value
    }
 
    class VehicleSpecifications {
        +String manufacturer
        +String model
        +VehicleCategory category
        +Transmission transmission
        +FuelType fuelType
        +Integer year
        +String color
        +Integer seats
    }
 
    class VehicleLocation {
        +String branchCode
        +String city
    }
 
    class VehicleDailyRate {
        +BigDecimal amount
        +String currency
    }
 
    class OdometerReading {
        +long kilometers
    }
 
    class VehicleStatus {
        <<enumeration>>
        AVAILABLE
        IN_MAINTENANCE
        DECOMMISSIONED
    }
 
    class VehicleCondition {
        <<enumeration>>
        GOOD
        NEEDS_INSPECTION
        DAMAGED
    }
 
    class VehicleCategory {
        <<enumeration>>
        ECONOMY
        COMPACT
        MIDSIZE
        SUV
        LUXURY
    }
 
    class Transmission {
        <<enumeration>>
        MANUAL
        AUTOMATIC
    }
 
    class FuelType {
        <<enumeration>>
        GASOLINE
        DIESEL
        ELECTRIC
        HYBRID
    }
 
    Vehicle *-- VehicleId
    Vehicle *-- LicensePlate
    Vehicle *-- VehicleSpecifications
    Vehicle *-- VehicleLocation
    Vehicle *-- VehicleDailyRate
    Vehicle *-- OdometerReading
    Vehicle *-- VehicleCondition
    Vehicle *-- VehicleStatus
    VehicleSpecifications *-- VehicleCategory
    VehicleSpecifications *-- Transmission
    VehicleSpecifications *-- FuelType
```

> Notas de fidelidade ao código: `LicensePlate` valida **no constructor** (compact) e
> normaliza com `trim()` + `toUpperCase(Locale.ROOT)`; `OdometerReading` é um record com
> checagem não-negativa, e o invariante de **monotonicidade** vive no agregado
> `Vehicle.recordOdometer` ("odometer cannot go backwards") — não no value object.
 
### Vehicle State Machine
 
```mermaid
stateDiagram-v2
    [*] --> AVAILABLE : register()
    AVAILABLE --> IN_MAINTENANCE : sendToMaintenance()
    IN_MAINTENANCE --> AVAILABLE : releaseFromMaintenance()
    AVAILABLE --> DECOMMISSIONED : decommission()
    IN_MAINTENANCE --> DECOMMISSIONED : decommission()
    DECOMMISSIONED --> [*] : (terminal)
 
    note right of AVAILABLE
        Can be offered for reservation
        Odometer can increase
        Condition can change
    end note
 
    note right of IN_MAINTENANCE
        Cannot be offered
        MaintenanceOrder acompanha o serviço
        (agregados separados, sem acoplamento)
    end note
 
    note right of DECOMMISSIONED
        status = DECOMMISSIONED (soft via status,
        não há coluna de soft delete na entidade)
        Cannot return to AVAILABLE
        🔜 sem checagem de reservas abertas
    end note
```
 
### Vehicle value objects
 
- `VehicleId`
- `LicensePlate`
- `VehicleSpecifications`
- `VehicleLocation`
- `VehicleDailyRate`
- `OdometerReading`
 
### Vehicle domain concepts
 
- `VehicleStatus`: AVAILABLE, IN_MAINTENANCE, DECOMMISSIONED
- `VehicleCondition`: GOOD, NEEDS_INSPECTION, DAMAGED
- `VehicleCategory`
- `Transmission`
- `FuelType`

### Maintenance concepts
 
- `MaintenanceOrderId`
- `MaintenanceType`: PREVENTIVE, CORRECTIVE, INSPECTION
- `MaintenanceStatus`: OPEN, IN_PROGRESS, COMPLETED, CANCELLED
- `MaintenanceOrder`
 
### MaintenanceOrder State Machine
 
```mermaid
stateDiagram-v2
    [*] --> OPEN : open()
    OPEN --> IN_PROGRESS : start()
    IN_PROGRESS --> COMPLETED : complete()
    OPEN --> CANCELLED : cancel()
    IN_PROGRESS --> CANCELLED : cancel()
    COMPLETED --> [*] : (terminal)
    CANCELLED --> [*] : (terminal)
 
    note right of OPEN
        Can transition to IN_PROGRESS or CANCELLED
    end note
 
    note right of IN_PROGRESS
        Work being performed
        Can complete or cancel
    end note
 
    note right of COMPLETED
        Terminal success state
        Cannot be cancelled
    end note
 
    note right of CANCELLED
        Terminal failure state
        Reason recorded
    end note
```
 
### Current behavior

Vehicle already supports:

- registration with default AVAILABLE status;
- decommissioning as a soft-delete lifecycle transition;
- entering/leaving maintenance;
- relocation;
- monotonically increasing odometer readings;
- explicit condition changes;
- immutable base daily rate.

MaintenanceOrder already models:

- opening;
- starting;
- completion;
- cancellation rules;
- rehydration from persistence.

### Useful future concepts

- maintenance work-order persistence;
- scheduled maintenance;
- inspection history;
- damage assessment;
- fleet branch aggregate;
- vehicle availability windows;
- odometer-based maintenance policies.

Availability for a rental period is **not owned by Inventory**. It is a derived concept involving reservations. Inventory owns whether the vehicle exists, is in service, its operational state and whether it can be offered.

### Invariants

- a license plate identifies one vehicle in the fleet;
- a decommissioned vehicle cannot return to AVAILABLE;
- a vehicle in maintenance cannot be offered;
- odometer readings cannot move backwards;
- a vehicle condition is explicit;
- required vehicle specification data must be valid;
- only OPEN maintenance orders can become IN_PROGRESS;
- only IN_PROGRESS maintenance orders can become COMPLETED;
- completed maintenance orders cannot be cancelled.

## 2. Reservation Context

### Responsibility

Own the booking commitment between a customer and a vehicle for a period.

### Aggregate root

`Reservation`

### Value objects

- `ReservationId`
- `CustomerId`
- `VehicleId`
- `RentalPeriod`

### Reservation status
 
PENDING, CONFIRMED, CANCELLED, REJECTED, COMPLETED.
 
### Reservation State Machine
 
```mermaid
stateDiagram-v2
    [*] --> PENDING : create()
    PENDING --> CONFIRMED : confirm()
    PENDING --> REJECTED : reject()
    PENDING --> CANCELLED : cancel()
    CONFIRMED --> CANCELLED : cancel()
    REJECTED --> CANCELLED : cancel()
    CONFIRMED --> COMPLETED : complete()
    CANCELLED --> [*] : (terminal)
    COMPLETED --> [*] : (terminal)

    note right of PENDING
        Awaiting vehicle availability
        check / pricing
    end note

    note right of CONFIRMED
        Vehicle reserved for period
        Ready for rental start
    end note

    note right of COMPLETED
        Rental finished
        Fatura/Invoice 🔜 (nenhum evento
        é produzido hoje por reservation)
    end note
```

`cancel()` só é recusado para `COMPLETED`/`CANCELLED`; por isso `REJECTED → CANCELLED` é
uma transição válida (REJECTED **não** é terminal). No código
(`Reservation.java`), `complete()` exige `CONFIRMED`.
 
### Invariants

- end date cannot precede start date;
- a cancelled/completed reservation cannot return to an invalid state;
- reservation state transitions must be explicit;
- overlapping active reservations for the same vehicle must be rejected.

The domain does not own the customer or vehicle aggregate. Their identities cross the context boundary as values.

## 3. Rental Context

### Responsibility

Own the physical rental lifecycle after a reservation is fulfilled.

### Aggregate root

`Rental`

### Value objects

- `RentalId`
- `ReservationId`
- `CustomerId`

### Lifecycle
 
```mermaid
stateDiagram-v2
    [*] --> ACTIVE : start(customerId, reservationId, startDate)
    ACTIVE --> COMPLETED : finish(finishedAt)
    ACTIVE --> CANCELLED : cancel()
    COMPLETED --> [*] : (terminal)
    CANCELLED --> [*] : (terminal)

    note right of ACTIVE
        Vehicle in customer possession
        odometer/fuel tracked (🔜)
    end note

    note right of COMPLETED
        Vehicle returned
        RentalCompleted 🔜 (billing consome o
        tópico, mas rental ainda não o publica)
    end note
```

`Rental.start()` já cria a locação em `ACTIVE`: o estado `PENDING` existe em
`RentalStatus` mas **não é produzido** — o branch de retirada (`pickupVehicle()`) ainda
não foi modelado, e `finish(finishedAt)` exige `ACTIVE` e `finishedAt >= startDate`.
`cancel()` é recusado a partir de `COMPLETED`.

Cancellation/failure paths should be explicit rather than represented by booleans.

Future concepts:

- VehicleId reference;
- pickup/return branch (🔜 `PENDING` no ciclo real);
- odometer at pickup and return;
- fuel level;
- inspection;
- damage report;
- late-return policy;
- extensions.

## 4. Pricing Context

Pricing is intentionally modeled as a future bounded context rather than putting pricing rules inside Reservation or Inventory.

Potential concepts:

- `PriceQuote`
- `Money`
- `RatePlan`
- `PricingRule`
- `RentalDuration`
- `OptionalExtra`
- `Promotion`

Inventory may expose vehicle category/specification information that Pricing consumes, but Inventory does not own dynamic pricing rules.

## 5. Billing / Payment Context

The billing service has the first domain foundation (Invoice DRAFT→OPEN driven by integration events)
plus the persisted inbox (ADR 005).

### Aggregate root

`Invoice`

### Supporting concepts

Classes that exist today in `billing-service/.../domain/model/` (além do aggregate root `Invoice`):

- `InvoiceId`
- `Money`
- `InvoiceLine`
- `InvoiceStatus`
- `PaymentMethod`
- `PaymentStatus`

Integration contracts (anti-corruption events, own per-context representation in
`application/event/`):

- `ReservationConfirmed` → `CreateInvoice` (invoice DRAFT, guessed period from reservation) 🔜 sem produtor
- `RentalCompleted` → `OpenInvoiceForRental` (effective dates + daily-rate price lock → OPEN) 🔜 sem produtor
- `VehicleRegistered` → `ConsumeVehicleRegistered` ✅ produtor real (`inventory-service`)
- `InvoiceOpened` → evento publicado pela outbox transacional; 🔜 sem consumidor
 
### Anti-Corruption Layer (Billing)
 
```mermaid
flowchart TB
    subgraph SRC["Contexts de origem"]
        ResEvent["reservation<br/>ReservationConfirmed 🔜"]
        RenEvent["rental<br/>RentalCompleted 🔜"]
        InvEvent["inventory<br/>VehicleRegistered ✅"]
    end

    subgraph INB["Inbound Messaging Adapters<br/>(billing adapter/in/messaging)"]
        ConsRes["KafkaReservationConfirmedConsumer<br/>+ event ReservationConfirmed"]
        ConsRen["KafkaRentalCompletedConsumer<br/>+ event RentalCompleted"]
        ConsInv["KafkaVehicleRegisteredConsumer<br/>+ event VehicleRegistered"]
        Inbox["TransactionalInboxProcessor<br/>tryClaim(eventId)"]
    end

    subgraph APP["Billing Application"]
        CreateInvoiceUC["CreateInvoice"]
        OpenInvoiceUC["OpenInvoiceForRental"]
        ConsumeVehicleUC["ConsumeVehicleRegistered"]
        PublishUC["PublishPendingOutboxEvents"]
        Outbox["OutboxEvent<br/>(application/model)"]
        OutboxStore["OutboxEventStore<br/>(port/out)"]
        EventPub["EventPublisher<br/>(port/out)"]
    end

    subgraph DOM["Billing Domain"]
        Invoice["Invoice (aggregate)<br/>DRAFT → OPEN"]
    end

    subgraph OUT["Billing Outbound Adapters"]
        Relay["OutboxRelay<br/>@Scheduled 5s"]
        PanacheStore["PanacheOutboxEventStore"]
        Pub["InvoiceOpenedKafkaPublisher<br/>tópico invoice-opened"]
    end

    ResEvent -.-> ConsRes
    RenEvent -.-> ConsRen
    InvEvent --> ConsInv

    ConsRes --> Inbox
    ConsRen --> Inbox
    ConsInv --> Inbox

    Inbox --> CreateInvoiceUC
    Inbox --> OpenInvoiceUC
    Inbox --> ConsumeVehicleUC

    CreateInvoiceUC --> Invoice
    OpenInvoiceUC --> Invoice
    CreateInvoiceUC -->|"appendInvoiceOpened"| Outbox
    OpenInvoiceUC -->|"appendInvoiceOpened"| Outbox

    Relay -->|"findPending(100)"| PublishUC
    PublishUC --> OutboxStore
    PublishUC --> EventPub
    OutboxStore -.->|"implementa"| PanacheStore
    EventPub -.->|"implementa"| Pub

    classDef external fill:#fce4ec,stroke:#c2185b,stroke-width:2px;
    classDef adapter fill:#f3e5f5,stroke:#6a1b9a,stroke-width:2px;
    classDef application fill:#e3f2fd,stroke:#1565c0,stroke-width:2px;
    classDef domain fill:#e8f5e9,stroke:#2e7d32,stroke-width:2px;

    class ResEvent,RenEvent,InvEvent external;
    class ConsRes,ConsRen,ConsInv,Inbox,Relay,PanacheStore,Pub adapter;
    class CreateInvoiceUC,OpenInvoiceUC,ConsumeVehicleUC,PublishUC,Outbox,OutboxStore,EventPub application;
    class Invoice domain;
```

The ACL is per-context by construction: the consumers in `adapter/in/messaging` translate each
foreign contract into the `application/event/*` representation before calling the use cases.
`reservation-service` and `rental-service` do not publish those events yet (🔜 — only the test
harness does), while `vehicle-registered` is produced for real. `ConsumeVehicleRegistered` is
hoje um **pass-through sem efeito de negócio** (apenas loga e confirma). `invoice-opened` deixa o
Billing pela outbox transacional: o `OutboxRelay` chama `PublishPendingOutboxEvents`, que publica
através do porto `EventPublisher`, implementado por `InvoiceOpenedKafkaPublisher`; o `OutboxEventStore`
(porto) é implementado por `PanacheOutboxEventStore`. O domínio do Billing não conhece nenhum
desses portos nem os adapters de mensageria.

Future concerns:

- payment authorization;
- payment reference;
- refunds/compensation;
- consumer of `invoice-opened` in the other contexts (🔜);
- asynchronous billing.

> Nota (cap.9): idempotência de consumidor é aplicada pelo `TransactionalInboxProcessor` com inbox durável (`PostgresProcessedEventStore`, `INSERT ... ON CONFLICT DO NOTHING`);
> claim e efeito de negócio compartilham a mesma transação reativa. O workflow DRAFT→OPEN via Kafka→Postgres está executável e coberto por testes
> (app/domain/persistence + `BillingFlowKafkaIntegrationTest`).

## 6. Users Service

`users-service` is a BFF/web adapter, not a duplicate Customer, Reservation or Inventory domain.

It may own:

- authenticated session representation;
- HTML/Qute models;
- browser-oriented orchestration.

Identity ownership remains with Keycloak/external identity infrastructure.

## 7. What is deliberately NOT shared

Never create a common `Car`, `Reservation`, `Rental`, `Customer` or `Money` object under a generic `shared` package merely to avoid mapping.

For example:

```
Inventory.domain.model.Vehicle
Reservation.domain.model.Reservation
Rental.domain.model.Rental
Billing.domain.model.Invoice
```

And at context boundaries:

```
Reservation.application.port.out.InventoryGateway
Reservation.application.query.AvailableVehicle
Users.application.model.ReservationView
```

These are intentionally different representations.

## 8. Domain maturity

### Phase 1 — baseline

- establish bounded contexts;
- establish aggregates and value objects;
- move invariants from resources/repositories into domain;
- establish application use cases and ports;
- introduce TDD at domain/application levels.

### Phase 2 — richer domain

- maintenance workflow;
- pricing;
- billing/payment;
- domain events;
- messaging.

### Phase 3 — distributed behavior

- idempotency;
- retries/timeouts;
- cancellation;
- observability;
- reactive composition;
- concurrency control;
- backpressure;
- consistency/compensation patterns.

The domain should become richer because the use cases require it, not because every DDD tactical pattern must appear in every class.

---
_Last updated: 2026-09-28 (diagramas sincronizados com o código; estilo Mermaid unificado)_
