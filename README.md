# ACME Car Rental

Laboratório de engenharia de software construído capítulo a capítulo com base no livro *Quarkus in Action*, evoluído com Domain-Driven Design (DDD), Test-Driven Development (TDD) e arquitetura distribuída.

## O que este projeto demonstra
- DDD por bounded contexts
- TDD como fluxo de implementação
- Quarkus 3.39.3 + Java 21
- REST, GraphQL e gRPC
- OIDC/Keycloak
- Hibernate ORM/Panache
- Hibernate Reactive/Panache + PostgreSQL reativo
- MongoDB/Panache
- Mutiny, event loop e isolamento de operações bloqueantes
- Reactive Messaging, eventos e resiliência como próximos passos
- native builds, Dev Services e experiência de desenvolvimento

## Arquitetura

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

Bounded contexts atuais:
- Inventory / Fleet
- Reservation
- Rental
- Billing / Payment
- Pricing (futuro)

Módulos especiais:
- users-service — BFF/UI
- inventory-cli — cliente administrativo
- inventory-proto — contrato gRPC

Veja [docs/domain.md](docs/domain.md) e [docs/architecture.md](docs/architecture.md).

## TDD

Cada comportamento novo deve seguir:

~~~text
Specification
     ↓
RED
     ↓
GREEN
     ↓
REFACTOR
~~~

Os testes são separados por responsabilidade:
- domain
- application
- adapter
- integration/native

Veja [docs/testing.md](docs/testing.md).

## OpenCode

O projeto possui uma camada de governança arquitetural em `.opencode/`:
- ddd-guardian
- tdd-guardian
- quarkus-book-guardian
- architecture-guardian
- domain-designer
- feature-implementer

Comandos:

~~~text
/domain-design <feature>
/preflight <feature>
/ddd-audit <service>
/tdd-audit <service>
/quarkus-audit <service>
/architecture-audit <scope>
~~~

O agente `feature-implementer` é o agente padrão definido em `opencode.json`.

## Domínio

Inventory usa `Vehicle` e `MaintenanceOrder` como aggregates. Vehicle possui value objects como `LicensePlate`, `VehicleSpecifications`, `VehicleLocation`, `VehicleDailyRate` e `OdometerReading`, além de `VehicleCondition` para o estado operacional.

Reservation usa `Reservation` + `RentalPeriod`.

Rental usa `Rental` com ciclo de vida explícito.

Billing inicia com `Invoice`, `InvoiceLine` e `Money`.

O objetivo é enriquecer o domínio conforme novos comportamentos são descobertos, sem transformar o projeto em uma coleção artificial de padrões DDD.

## Roadmap

O desenvolvimento acompanha o livro em [docs/roadmap.md](docs/roadmap.md).

A documentação técnica consolidada está em [docs/README.md](docs/README.md).