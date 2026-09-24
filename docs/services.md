# Serviços — ACME Car Rental

> Fonte da verdade: código + docs/domain.md + docs/ddd-tdd-standards.md.

Todos os serviços de negócio seguem o mesmo idioma arquitetural: **Domain → Application → Ports → Adapters**. As diferenças são explicadas pelo bounded context ou pelo papel técnico do módulo.

## inventory-service

**Bounded Context:** Inventory / Fleet.

Responsável pela frota, ciclo de vida e estado operacional dos veículos.

### Aggregate roots

- `Vehicle`
- `MaintenanceOrder`

### Vehicle domain

Value objects:
- `VehicleId`
- `LicensePlate`
- `VehicleSpecifications`
- `VehicleLocation`
- `VehicleDailyRate`
- `OdometerReading`

Concepts:
- `VehicleStatus`: AVAILABLE, IN_MAINTENANCE, DECOMMISSIONED
- `VehicleCondition`: GOOD, NEEDS_INSPECTION, DAMAGED
- `VehicleCategory`
- `Transmission`
- `FuelType`

Behavior:
- registration starts AVAILABLE;
- decommissioning is a state transition/soft delete;
- maintenance state transitions are explicit;
- relocation is explicit;
- odometer cannot decrease;
- condition is explicit;
- base daily rate is immutable.

### Maintenance domain

`MaintenanceOrder` models a maintenance work lifecycle:

`OPEN → IN_PROGRESS → COMPLETED`

with cancellation rules and `MaintenanceOrderId`, `MaintenanceType`, `MaintenanceStatus`.

Future behavior can add scheduled maintenance, inspections, damage assessment and odometer-based maintenance policies.

### Application use cases

- `RegisterVehicle`
- `SearchVehicles`
- `FindVehicleByPlate`
- `ListVehicles`
- `DecommissionVehicle`

### Adapters

Inbound:
- GraphQL
- gRPC

Outbound:
- JPA/Panache + MySQL
- Kafka out (`EventPublisher`) → tópico `vehicle-registered` (cap.9)

GraphQL exposes a transport DTO named `Car` for compatibility with the existing laboratory contract. It is not a domain object.

The GraphQL adapter maps filtering/sorting/pagination to application query objects; the application use case owns that selection logic.

## reservation-service

**Bounded Context:** Reservation.

Owns the booking commitment between customer and vehicle for a rental period.

Aggregate:
- `Reservation`

Value objects:
- `ReservationId`
- `CustomerId`
- `VehicleId`
- `RentalPeriod`

States:
- PENDING
- CONFIRMED
- CANCELLED
- REJECTED
- COMPLETED

Use cases:
- `CreateReservation`
- `FindAvailableVehicles`
- `ListReservations`

Ports:
- `ReservationRepository`
- `InventoryGateway`
- `RentalGateway`

Adapters:
- REST in
- OIDC/security in
- GraphQL out to Inventory
- REST out to Rental
- Hibernate Reactive/Panache out to PostgreSQL

Availability is derived in the Reservation context by combining Inventory data and reservation conflicts. Inventory does not own period availability.

## rental-service

**Bounded Context:** Rental.

Owns the physical rental lifecycle.

Aggregate:
- `Rental`

Value objects:
- `RentalId`
- `ReservationId`
- `CustomerId`

States:
- PENDING
- ACTIVE
- COMPLETED
- CANCELLED

Use cases:
- `StartRental`
- `EndRental`
- `ListRentals`

Adapter in:
- REST

Adapter out:
- MongoDB/Panache

The domain uses `RentalStatus`; the persistence representation may keep compatibility fields as required by the current schema.

## billing-service

**Bounded Context:** Billing / Payment.

Aggregate:
- `Invoice`

Domain:
- `Invoice`
- `InvoiceLine`
- `InvoiceId`
- `Money`
- `InvoiceStatus`
- `PaymentMethod`
- `PaymentStatus`

The current implementation is a domain/application foundation. Inbound adapters (cap.9): Kafka consumers
`reservation-confirmed-in`/`rental-completed-in`/`vehicle-registered-in` (group `billing-service`)
reagem a eventos de integração de Reservation/Rental/Inventory. O fluxo de cobrança executa
DRAFT→OPEN: `ReservationConfirmed` cria a fatura DRAFT; `RentalCompleted` recompõe a linha com as datas
efetivas (price lock da tarifa diária) e abre a fatura (`CreateInvoice`/`OpenInvoiceForRental`),
persistida em PostgreSQL (Hibernate Reactive Panache).

Idempotência é aplicada pela fronteira transacional `TransactionalInboxProcessor` (ADR 007). O consumer entrega o `eventId` e o efeito de negócio ao processor; o `ProcessedEventStore` permanece atrás de uma porta.

Retry de processamento é tratado na infraestrutura de messaging (estratégia `delayed-retry-topic` do conector
Kafka, `max-retries=3`; ver ADR 002) e a DLQ após o esgotamento (ADR 003). O inbox durável usa
`PostgresProcessedEventStore` com claim atômico (`INSERT ... ON CONFLICT DO NOTHING`; ver ADR 005).

## users-service

**Role:** Web BFF.

It owns browser-facing orchestration and presentation models, not the Reservation/Inventory domain.

Application:
- `ReservationFacade`

Port:
- `ReservationsGateway`

Adapters:
- Qute/web
- OIDC/security
- REST to reservation

Transport models from reservation remain inside the outbound adapter.

## inventory-cli

**Role:** administrative client.

Application:
- `InventoryAdminGateway`

Adapters:
- CLI in
- gRPC out

Not a bounded context.

## inventory-proto

Contract-only module.

Contains the protobuf schema and build configuration used to generate consumer/server stubs. No domain logic.

## Dependency rule

~~~text
adapter in
   ↓
application
   ↓
domain
   ↑
application port out
   ↑
adapter out
~~~

Models do not cross bounded-context boundaries merely to avoid mapping.

Messaging cross-cutting concerns such as idempotency belong to the messaging infrastructure, not to individual business consumers.

## Migration status

| Module | Domain boundary | Application/use cases | TDD foundation | Special profile |
|---|---|---|---|---|
| inventory-service | ✅ | ✅ | ✅ | business service |
| reservation-service | ✅ | ✅ | ✅ | business service |
| rental-service | ✅ | ✅ | ✅ | business service |
| billing-service | ✅ | ✅ | ✅ | business service foundation |
| users-service | ✅ | ✅ | ✅ | BFF |
| inventory-cli | ✅ | ✅ | — | client |
| inventory-proto | ✅ | — | — | contract |
