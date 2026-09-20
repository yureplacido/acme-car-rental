# ACME Car Rental — Engineering Rules

Before changing code, read:
- `docs/ddd-tdd-standards.md`
- `docs/domain.md`
- `docs/architecture.md`
- `docs/services.md`
- `docs/testing.md`
- `docs/roadmap.md`

## Non-negotiable rules

1. Every new business behavior starts with a test (TDD):
   `Red -> Green -> Refactor`.
2. Do not add framework code to the domain model.
3. Domain code must not depend on Quarkus, REST, GraphQL, gRPC, Panache, JPA, MongoDB, CDI, OIDC, Kafka, RabbitMQ, or Mutiny.
4. Application use cases orchestrate domain behavior and ports. They may use Mutiny when asynchronous I/O is part of the use case.
5. Infrastructure/framework concerns belong in adapters.
6. Do not share domain entities, persistence entities, or transport DTOs across bounded contexts.
7. Cross-context communication uses explicit contracts and anti-corruption adapters.
8. Do not introduce a second architectural pattern just for one service. Follow the service profile defined in `docs/ddd-tdd-standards.md`.
9. Do not create generic `model`, `service`, `repository`, or `util` packages when the class has a more precise domain/application/adapter responsibility.
10. Domain rules belong to aggregates/value objects/domain services, not REST resources or repositories.
11. Persistence implementations must be isolated from domain and application business rules.
12. Every new adapter or use case must have tests at the appropriate layer.
13. Reactive code must not block the event loop. Blocking work must be isolated deliberately.
14. Do not use `Uni` or `Multi` as a substitute for understanding concurrency, failure, cancellation, timeouts, or backpressure.
15. Preserve the concepts demonstrated by the book and record progress in `docs/roadmap.md`.
16. Before adding a Quarkus API/configuration that is version-sensitive, verify it against the project's pinned Quarkus version (`3.39.3`) and official documentation.
17. Do not mark a chapter or architectural milestone complete without executable evidence in code/tests and corresponding documentation.
18. Prefer small, behavior-oriented commits. Keep refactors separate from behavior changes when practical.

## Standard change flow

1. Identify bounded context and use case.
2. Write/adjust the behavioral specification.
3. Add the smallest failing test.
4. Implement the minimum behavior.
5. Refactor to the target DDD structure without changing behavior.
6. Add/adjust adapter and integration tests.
7. Run the service test suite.
8. Run the relevant architecture/consistency review agents.
9. Update docs/roadmap when the change advances a book chapter or architectural decision.

## Architecture gate

Before accepting a change, the relevant reviewer agents should agree that:
- DDD boundaries are respected.
- TDD evidence is present.
- the service follows its profile.
- Quarkus/book concepts are applied intentionally.
- no new local convention contradicts the repository-wide standard.
