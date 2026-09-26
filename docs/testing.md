# Testes — ACME Car Rental

> Estratégia única para todo o repositório. Cada serviço aplica as camadas de teste de acordo com seu papel.
> Para o **porquê** de cada regra (e as lições das armadilhas reais), ver a base de estudo:
> [knowledge/04-estrategia-de-testes-do-projeto.md](./knowledge/04-estrategia-de-testes-do-projeto.md)
> e [knowledge/11-armadilhas-e-licoes.md](./knowledge/11-armadilhas-e-licoes.md).

## Pirâmide de testes

~~~text
              Integration / Native
                     ▲
             Adapter / Contract
                     ▲
            Application Use Cases
                     ▲
               Domain tests
~~~

### Domain
JUnit puro.

Sem Quarkus, CDI, banco, HTTP, GraphQL, gRPC, Panache ou Mutiny.

Objetivo: provar invariantes e comportamentos do domínio.

Exemplos atuais:
- inventory-service/src/test/java/org/acme/inventory/domain/VehicleTest.java
- reservation-service/src/test/java/org/acme/reservation/domain/ReservationTest.java
- rental-service/src/test/java/org/acme/rental/domain/RentalTest.java
- billing-service/src/test/java/org/acme/billing/domain/InvoiceTest.java

### Application
Testes JVM dos casos de uso usando ports/fakes/mocks.

Exemplos atuais: RegisterVehicle, CreateReservation, StartRental, CreateInvoice, ReservationFacade.

O teste deve poder rodar sem subir a aplicação Quarkus.

### Adapter
Usar @QuarkusTest quando a fronteira/framework é parte do comportamento.

Exemplos: Reservation REST, Reservation persistence reativa, GraphQL/gRPC contracts e OIDC/security.

### Integration / native
@QuarkusIntegrationTest é reservado para validar o artefato empacotado e o runtime.

## TDD

Cada mudança de comportamento deve seguir:

~~~text
Specification
     ↓
RED
     ↓
GREEN
     ↓
REFACTOR
~~~

Um teste criado depois da implementação não é evidência suficiente de TDD.

Os commits devem preferencialmente manter essa história visível: test(...), feat(...), refactor(...).

## Nomenclatura
Prefira nomes como shouldRejectReservationWhenVehicleIsAlreadyReserved e evite nomes que descrevem implementação.

## Reactive testing
Testes reativos devem provar comportamento da pipeline. Não use await().indefinitely() para esconder um contrato assíncrono em código que deveria permanecer não bloqueante.

Em teste anotado com @RunOnVertxContext (que roda na event loop do Vert.x) é proibido await().indefinitely(): o await bloqueia a thread que precisa entregar o item, gerando deadlock. Nesses casos use UniAsserter/AssertSubscriber, ou isole a operação bloqueante com runSubscriptionOn(...).runSubscriptionOnIn(workerThread). Ver docs/knowledge/04-estrategia-de-testes-do-projeto.md §5.

Para Hibernate Reactive, o Quarkus fornece suporte específico de teste e exige contexto/sessão reativa apropriados. Ver <https://quarkus.io/guides/hibernate-reactive-panache>.