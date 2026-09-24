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
    Billing[Billing / Payment]
    Pricing[Pricing]
    Identity[Keycloak]

    Users --> Reservation
    Reservation --> Inventory
    Reservation --> Rental
    Rental --> Billing
    Reservation --> Pricing
    Users --> Identity
    Reservation --> Identity
~~~

### Inventory / Fleet
Dono da frota, dos veículos e de seu estado operacional.
Aggregates: Vehicle e MaintenanceOrder.
Vehicle também mantém condition e odometer; MaintenanceOrder modela o workflow de manutenção.
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
│   ├── VehicleCondition.java
│   ├── OdometerReading.java
│   ├── MaintenanceOrder.java
│   ├── MaintenanceOrderId.java
│   ├── MaintenanceType.java
│   ├── MaintenanceStatus.java
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
│   │   ├── CreateInvoice.java          (ReservationConfirmed -> invoice DRAFT)
│   │   ├── OpenInvoiceForRental.java   (RentalCompleted -> linha efetiva + OPEN)
│   │   ├── ConsumeReservationConfirmed.java
│   │   ├── ConsumeRentalCompleted.java
│   │   └── ConsumeVehicleRegistered.java
│   ├── event/
│   │   ├── ReservationConfirmed.java   (contrato anti-corrupção)
│   │   ├── RentalCompleted.java        (contrato anti-corrupção)
│   │   └── VehicleRegistered.java
│   └── port/out/
│       ├── InvoiceRepository.java
│       └── ProcessedEventStore.java
└── adapter/
    ├── in/messaging/
    │   ├── KafkaReservationConfirmedConsumer.java
    │   ├── KafkaRentalCompletedConsumer.java
    │   ├── KafkaVehicleRegisteredConsumer.java
    │   └── TransactionalInboxProcessor.java
    └── out/
        ├── messaging/InMemoryProcessedEventStore.java
        └── persistence/
            ├── PanacheInvoiceRepository.java
            ├── InvoiceEntity.java / InvoiceMapper.java / InvoiceLinesConverter.java
            ├── PostgresProcessedEventStore.java (inbox durável, ADR 005)
            └── ProcessedEventEntity.java
~~~

O fluxo de cobrança (cap.9) consome `reservation-confirmed`/`rental-completed` e persiste invoices em
PostgreSQL (DRAFT→OPEN), aplicando idempotência via `TransactionalInboxProcessor` (ADR 007) e inbox durável via `PostgresProcessedEventStore`
(ADR 005); retry via `delayed-retry-topic`
(ADR 002) e DLQ (ADR 003).

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

Quarkus documenta Hibernate Reactive como API voltada a acesso não bloqueante; `@WithTransaction` é a anotação usada para fronteiras transacionais reativas em métodos CDI que retornam `Uni`. Ver <https://quarkus.io/guides/hibernate-reactive> e <https://quarkus.io/guides/hibernate-reactive-panache>.

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
| 11 | Inventory separa Vehicle de MaintenanceOrder | lifecycle da frota e workflow de manutenção têm limites distintos |
| 12 | Consultas de seleção ficam na Application | adapters traduzem protocolo, não acumulam regra de consulta |
| 13 | OpenCode funciona como architecture gate | impedir divergência entre futuras implementações |
| 14 | Agregador raiz **somente para testes** (`packaging=pom`, sem parent/dependencyManagement) | rodar todos os testes com `./mvnw test` sem acoplar os microserviços |

## Construção e testes

Cada serviço é um microserviço independente: build, versionamento de dependências e deploy isolados,
sem parent compartilhado. O `pom.xml` da raiz é um **agregador de conveniência** (packaging `pom`,
apenas `<modules>`): ele conhece os módulos, mas nenhum módulo o conhece — nenhuma herança, nenhum
`dependencyManagement`, nenhuma configuração de plugin é imposta aos serviços.

- `./mvnw test` na raiz executa os testes de todos os módulos em ordem de reactor.
- Subconjunto: `./mvnw -pl reservation-service -am test`.
- O reactor resolve `org.acme:inventory-proto` diretamente do módulo `inventory-proto` (validado
  inclusive para o codegen `scan-for-proto` do Quarkus), dispensando a instalação manual no `~/.m2`
  quando o build parte do agregador.

Os módulos continuam sendo implantáveis e testáveis isoladamente (`./mvnw test` dentro de cada modulo).

### Portas em teste

`@QuarkusTest` usa porta de teste aleatória por padrão e o REST-assured acompanha a mesma porta,
por isso os testes não fixam URLs com porta. O agregador força essa regra com
`.mvn/maven.config` (`-Dquarkus.http.test-port=0`) em qualquer `./mvnw` na raiz. Módulos não devem
definir `quarkus.http.test-port` fixo — evita colisões quando há execuções simultâneas ou builds
paralelos (`-T`).

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