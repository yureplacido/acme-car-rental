# Serviços — ACME Car Rental

> Fonte da verdade: código + docs/domain.md + docs/ddd-tdd-standards.md.

Todos os serviços de negócio seguem o mesmo idioma arquitetural: domain, application, ports e adapters. A diferença entre eles é determinada pelo bounded context e pelo papel do módulo.

## inventory-service

Responsável pela frota e pelo ciclo de vida dos veículos.

Aggregate root: Vehicle.

Domínio:
- VehicleId
- LicensePlate
- VehicleSpecifications
- VehicleLocation
- VehicleStatus
- VehicleCategory
- Transmission
- FuelType

Casos de uso atuais:
- RegisterVehicle
- ListVehicles
- DecommissionVehicle

Adapters de entrada: GraphQL e gRPC.
Adapter de saída: persistência JPA/Panache sobre MySQL.

GraphQL continua expondo o conceito externo Car para compatibilidade do laboratório; Car é DTO de transporte, não objeto de domínio.

O antigo CarInventoryService foi removido. Regras como descomissionamento agora pertencem ao aggregate Vehicle.

## reservation-service

Responsável por reservas.

Aggregate root: Reservation.

Value objects:
- ReservationId
- CustomerId
- VehicleId
- RentalPeriod

Estados: PENDING, CONFIRMED, CANCELLED, REJECTED, COMPLETED.

Casos de uso:
- CreateReservation
- FindAvailableVehicles
- ListReservations

Ports de saída:
- ReservationRepository
- InventoryGateway
- RentalGateway

Adapters:
- REST de entrada
- segurança OIDC
- GraphQL de saída para inventory
- REST de saída para rental
- Hibernate Reactive/Panache para PostgreSQL

Disponibilidade é calculada pelo Reservation context combinando veículos do Inventory e reservas sobrepostas.

## rental-service

Responsável pelo ciclo de vida físico da locação.

Aggregate root: Rental.

Estados: PENDING, ACTIVE, COMPLETED, CANCELLED.

Casos de uso:
- StartRental
- EndRental
- ListRentals

Adapter de entrada: REST.
Adapter de saída: MongoDB/Panache.

O booleano active deixou de ser o modelo de domínio. A persistência pode manter compatibilidade física quando necessário, mas a regra de negócio usa RentalStatus.

## billing-service

Responsável futuro por cobrança e pagamento.

Aggregate root inicial: Invoice.

Domínio inicial:
- Invoice
- InvoiceLine
- Money
- InvoiceStatus
- PaymentMethod
- PaymentStatus

CreateInvoice já existe como caso de uso. A persistência real e os fluxos de mensageria entram nos capítulos seguintes.

## users-service

BFF/interface web.

Não possui cópia do domínio de Reservation ou Inventory.

Application:
- ReservationFacade

Ports:
- ReservationsGateway

Adapters:
- web/Qute
- security/OIDC
- REST para reservation

Os modelos de Reservation e Car do BFF são modelos de transporte do adapter.

## inventory-cli

Cliente administrativo do Inventory.

Application:
- InventoryAdminGateway

Adapters:
- CLI
- gRPC

O CLI não é bounded context.

## inventory-proto

Módulo de contrato gRPC.

Contém apenas schema/protocolo. Não possui domínio.

## Padrão de dependências

~~~text
adapter in
   ↓
application use case
   ↓
domain
   ↑
application port out
   ↑
adapter out
~~~

Os fluxos de dados podem atravessar contextos; os modelos de domínio não.

## Estado da migração

| Serviço | DDD baseline | TDD baseline | Observação |
|---|---|---|---|
| inventory | ✅ | ✅ | Vehicle aggregate + tests |
| reservation | ✅ | ✅ | use cases + reactive persistence |
| rental | ✅ | ✅ | lifecycle aggregate |
| billing | ✅ | ✅ | domain foundation |
| users | ✅ | ✅ | BFF profile |
| inventory-cli | ✅ | — | client profile |
| inventory-proto | ✅ | — | contract-only |