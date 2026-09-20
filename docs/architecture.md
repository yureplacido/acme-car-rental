# Arquitetura — ACME Car Rental

> Fonte da verdade: código + docs/ddd-tdd-standards.md + docs/domain.md.
> Java 21 · Quarkus 3.39.3.

## Visão

O projeto é um laboratório de engenharia para aplicar progressivamente os conceitos do Quarkus in Action junto com DDD, TDD e arquitetura distribuída.

Os serviços de negócio usam o mesmo sentido arquitetural:

~~~text
Inbound Adapter
      ↓
Application Use Case
      ↓
Domain
      ↑
Output Port
      ↑
Outbound Adapter
~~~

A infraestrutura conhece o domínio. O domínio não conhece a infraestrutura.

## Bounded contexts

~~~mermaid
flowchart LR
    Users[Users BFF]
    Reservation[Reservation]
    Inventory[Inventory]
    Rental[Rental]
    Billing[Billing]
    Identity[Keycloak]

    Users --> Reservation
    Reservation --> Inventory
    Reservation --> Rental
    Rental --> Billing
    Users --> Identity
    Reservation --> Identity
~~~

### Inventory
Dono da frota e ciclo de vida dos veículos.
Aggregate root: Vehicle.
O inventário não possui reservas e não decide disponibilidade temporal.

### Reservation
Dono do compromisso de reserva.
Aggregate root: Reservation.
Conhece apenas IDs externos (CustomerId, VehicleId) e contratos de outras fronteiras.

### Rental
Dono do ciclo de vida físico da locação.
Aggregate root: Rental.

### Billing
Dono futuro de faturas e pagamento.
Aggregate root inicial: Invoice.

### Users
BFF/presentation service. Não duplica os aggregates de Reservation ou Inventory.

## Comunicação

| Origem | Destino | Canal | Responsabilidade |
|---|---|---|---|
| users | reservation | REST | interação do navegador |
| reservation | inventory | GraphQL | consulta de catálogo/disponibilidade |
| reservation | rental | REST | iniciar locação imediata |
| inventory-cli | inventory | gRPC | administração e bulk |
| future billing | other contexts | messaging/REST | cobrança e eventos |

Contratos externos nunca atravessam a aplicação como modelos de domínio.

## Inventory — estrutura

~~~text
inventory-service/src/main/java/org/acme/inventory/
├── domain/model/
│   ├── Vehicle.java
│   ├── VehicleId.java
│   ├── LicensePlate.java
│   ├── VehicleSpecifications.java
│   ├── VehicleLocation.java
│   ├── VehicleStatus.java
│   ├── VehicleCategory.java
│   ├── Transmission.java
│   └── FuelType.java
├── application/
│   ├── usecase/
│   └── port/out/
└── adapter/
    ├── in/graphql/
    ├── in/grpc/
    └── out/persistence/
~~~

## Reservation — estrutura

~~~text
reservation-service/src/main/java/org/acme/reservation/
├── domain/model/
│   ├── Reservation.java
│   ├── ReservationStatus.java
│   ├── RentalPeriod.java
│   ├── CustomerId.java
│   ├── VehicleId.java
│   └── ReservationId.java
├── application/
│   ├── usecase/
│   ├── query/
│   └── port/out/
└── adapter/
    ├── in/rest/
    ├── in/security/
    └── out/
        ├── inventory/
        ├── rental/
        └── persistence/
~~~

## Rental — estrutura

~~~text
rental-service/src/main/java/org/acme/rental/
├── domain/model/
├── application/
│   ├── usecase/
│   └── port/out/
└── adapter/
    ├── in/rest/
    └── out/persistence/
~~~

## Billing — estrutura inicial

~~~text
billing-service/src/main/java/org/acme/billing/
├── domain/model/
│   ├── Invoice.java
│   ├── InvoiceLine.java
│   ├── Money.java
│   ├── InvoiceStatus.java
│   ├── PaymentMethod.java
│   └── PaymentStatus.java
├── application/
│   ├── usecase/
│   └── port/out/
└── adapter/
    └── out/persistence/
~~~

A persistência de Billing permanece propositalmente simples até o capítulo de banco/messaging correspondente.

## Users BFF

~~~text
users-service/src/main/java/org/acme/users/
├── application/
│   ├── usecase/
│   └── port/out/
└── adapter/
    ├── in/security/
    ├── in/web/
    └── out/reservation/
~~~

O BFF adapta o modelo remoto de Reservation para a UI. Ele não importa a classe Reservation do reservation-service.

## CLI

~~~text
inventory-cli/src/main/java/org/acme/inventory/cli/
├── application/
└── adapter/
    ├── in/cli/
    └── out/grpc/
~~~

## Persistência

Cada bounded context possui seu próprio modelo de persistência.

~~~text
Domain Aggregate
      ↓
Repository Port
      ↓
Panache Adapter
      ↓
JPA / MongoDB / Reactive SQL
~~~

A documentação mantém a distinção entre domínio, application, adapter e persistence entity.

## Reactive

Reservation já usa Hibernate Reactive + Mutiny. A regra arquitetural é:

- domínio não usa Uni/Mutiny;
- aplicação pode compor I/O reativo;
- adapters usam clientes reativos;
- operações bloqueantes são isoladas;
- backpressure é tratado como capacidade do fluxo, não como sinônimo de thread pool.

Quarkus documenta Hibernate Reactive como API voltada a acesso não bloqueante e alto nível de concorrência; WithTransaction cria a fronteira transacional reativa para métodos CDI que retornam Uni. citeturn679014search0turn679014search1

## Decisões

| # | Decisão | Motivo |
|---|---|---|
| 1 | Serviços sem módulo Maven agregador | independência dos serviços |
| 2 | DDD por bounded context | preservar ownership |
| 3 | Domain/Application/Ports/Adapters | direção clara de dependências |
| 4 | DTOs nas bordas | impedir vazamento de contratos |
| 5 | Panache somente em adapters | manter domínio puro |
| 6 | TDD por comportamento | design guiado por feedback |
| 7 | REST/GraphQL/gRPC como adapters | transporte não é domínio |
| 8 | Users como BFF | não criar falso bounded context |
| 9 | Pricing fora do Inventory | evitar acoplamento semântico |
| 10 | Billing começa com Invoice/Money | preparar domínio sem inventar workflow |

## Estratégia de evolução

~~~text
Behavior
  ↓
Domain/Application test
  ↓
Minimal implementation
  ↓
Adapter
  ↓
Integration test
  ↓
Refactor
~~~

A aplicação só ganha complexidade quando um comportamento exigir essa complexidade.