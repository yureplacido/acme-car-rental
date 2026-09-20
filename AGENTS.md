# ACME Car Rental — Engineering Rules

Before changing code, read:
- `docs/ddd-tdd-standards.md`
- `docs/domain.md`
- `docs/architecture.md`
- `docs/services.md`
- `docs/testing.md`
- `docs/roadmap.md`

## Non-negotiable rules

1. Every new business behavior starts with a test (TDD): `Red -> Green -> Refactor`.
2. Domain code must not depend on Quarkus, REST, GraphQL, gRPC, Panache, JPA, MongoDB, CDI, OIDC, Kafka, RabbitMQ, or Mutiny.
3. Application use cases orchestrate domain behavior and ports. They may use Mutiny when asynchronous I/O is part of the use case.
4. Infrastructure/framework concerns belong in adapters.
5. Do not share domain entities, persistence entities, or transport DTOs across bounded contexts.
6. Cross-context communication uses explicit contracts and anti-corruption adapters.
7. Follow the service profile in `docs/ddd-tdd-standards.md`; services must not invent local architectural dialects without documenting the reason.
8. Do not create generic `model`, `service`, `repository`, or `util` packages when the class has a more precise domain/application/adapter responsibility.
9. Domain rules belong to aggregates, value objects and real domain services, not REST resources or repositories.
10. Inbound adapters map transport data to application commands/queries; they should not own business filtering, pagination, sorting or state-transition rules.
11. Persistence implementations must be isolated from domain and application business rules.
12. Every new use case/adapter must have tests at the appropriate layer.
13. Reactive code must not block the event loop. Blocking work must be isolated deliberately.
14. `Uni`/ `Multi` are execution models, not substitutes for reasoning about concurrency, failure, cancellation, timeouts and backpressure.
15. Before coding a new domain behavior, use `domain-designer` and then the architecture/TDD/Quarkus guardians.
16. Before adding a Quarkus API/configuration that is version-sensitive, verify it against the project's pinned Quarkus version (`3.39.3`) and official documentation.
17. Do not mark a chapter or architectural milestone complete without executable evidence in code/tests and corresponding documentation.
18. Prefer small behavior-oriented commits. Keep behavior changes and pure refactors separate when practical.

## Standard change flow

1. Identify bounded context and use case.
2. Design the domain and invariants.
3. Write the smallest failing test.
4. Implement the minimum behavior.
5. Refactor without changing behavior.
6. Add adapter/integration evidence where the boundary requires it.
7. Run the service test suite.
8. Run the OpenCode guardians.
9. Update documentation/roadmap.

## Architecture gate

A feature is ready to merge only when:
- DDD boundaries are respected.
- TDD evidence is present.
- the service follows its profile.
- Quarkus/book concepts are applied intentionally.
- no new local convention contradicts the repository standard.
