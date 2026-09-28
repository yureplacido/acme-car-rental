# Developer Onboarding — ACME Car Rental

> **Audience:** Developers joining the project.  
> **Goal:** Get productive in < 30 minutes.

---

## 1. Mental Model (2 min)

```
┌─────────────────────────────────────────────────────────────────┐
│                    ACME Car Rental (7 módulos)                   │
├──────────────┬──────────────┬─────────────┬─────────────────────┤
│ inventory-   │ reservation- │ rental-     │ billing-            │
│ service      │ service      │ service     │ service             │
│ (GraphQL,    │ (REST,       │ (REST,      │ (Kafka,             │
│  gRPC,       │  GraphQL     │  MongoDB)   │  PostgreSQL,        │
│  MySQL)      │  client,     │             │  Outbox/Inbox)      │
│              │  PostgreSQL) │             │                     │
├──────────────┼──────────────┼─────────────┼─────────────────────┤
│ users-       │ inventory-   │ inventory-  │                     │
│ service      │ cli          │ proto       │                     │
│ (BFF/Qute,   │ (gRPC        │ (protobuf   │                     │
│  OIDC)       │  client)     │  contract)  │                     │
└──────────────┴──────────────┴─────────────┴─────────────────────┘
```

**Key principle:** Domain code knows nothing about Quarkus, REST, JPA, Kafka, etc. Infrastructure adapts to domain, not vice versa.

---

## 2. Prerequisites

| Tool | Version | Notes |
|------|---------|-------|
| Java | 21 | Temurin/Adoptium recommended |
| Maven | 3.9+ | Wrapper included (`./mvnw`) |
| Docker | 24+ | For Dev Services (MySQL, Kafka, Postgres) |
| Podman | Optional | Alternative to Docker |

---

## 3. First Build & Test (5 min)

```bash
# Clone
git clone <repo-url>
cd acme-car-rental

# Run all tests (uses Dev Services - auto-starts MySQL/Kafka/Postgres in containers)
./mvnw test

# Run single service tests
./mvnw -pl inventory-service test
./mvnw -pl reservation-service test
./mvnw -pl billing-service test
```

**Expected:** All 39 tests in inventory-service pass, similar for other services.

---

## 4. Run Services Locally (Dev Mode)

Each service runs independently on its own port:

```bash
# Terminal 1: Inventory (GraphQL on :8083, gRPC on :9000)
cd inventory-service && ./mvnw quarkus:dev

# Terminal 2: Reservation (REST on :8082)
cd reservation-service && ./mvnw quarkus:dev

# Terminal 3: Rental (REST on :8084)
cd rental-service && ./mvnw quarkus:dev

# Terminal 4: Billing (Kafka consumer, REST on :8085)
cd billing-service && ./mvnw quarkus:dev

# Terminal 5: Users BFF (Web on :8081)
cd users-service && ./mvnw quarkus:dev
```

**Dev Services** auto-provision (fixed ports in `%dev` profile):
- MySQL: `localhost:33306`
- PostgreSQL: `localhost:55432` / `55433`
- Kafka: `localhost:39092`
- Keycloak: `localhost:38180` / `38181`

---

## 5. Architecture Quick Reference

### Layer Structure (every business service)

```
src/main/java/org/acme/<context>/
├── domain/
│   ├── model/          # Aggregates, Value Objects, Enums (PURE JAVA)
│   ├── service/        # Domain Services (rare)
│   ├── event/          # Domain Events
│   └── exception/      # Domain Exceptions
├── application/
│   ├── usecase/        # Use Cases (commands)
│   ├── query/          # Query Objects, Pagination
│   └── port/out/       # Output Ports (interfaces)
└── adapter/
    ├── in/             # REST, GraphQL, gRPC, CLI
    │   ├── rest/
    │   ├── graphql/
    │   └── grpc/
    └── out/            # Persistence, Messaging, External Clients
        ├── persistence/
        ├── messaging/
        └── rest/
```

### Dependency Direction (enforced)

```
Inbound Adapter → Application → Domain ← Output Port ← Outbound Adapter
```

**Rules:**
- Domain: **Zero** framework imports (no `jakarta.*`, `io.quarkus.*`, `io.smallrye.mutiny.*`, Panache, JPA)
- Application: Can use `Uni`/`Multi` only for async I/O orchestration
- Adapters: Framework code lives here

---

## 6. Common Tasks

### Add a New Domain Behavior (TDD Flow)

```bash
# 1. Design (use domain-designer agent)
/domain-design "Vehicle needs maintenance scheduling"

# 2. Write failing domain test (RED)
#    inventory-service/src/test/java/org/acme/inventory/domain/VehicleTest.java

# 3. Implement minimum behavior (GREEN)
#    inventory-service/src/main/java/org/acme/inventory/domain/model/Vehicle.java

# 4. Refactor (REFACTOR)

# 5. Add application use case + test
#    inventory-service/src/main/java/org/acme/inventory/application/usecase/ScheduleMaintenance.java

# 6. Add adapter (GraphQL/gRPC/REST) + test

# 7. Run guardians
/preflight "schedule-maintenance"
```

### Add a New REST Endpoint (Reservation Example)

1. **Domain** (if new behavior): `Reservation.java` + test
2. **Application**: `CreateReservation.java` (use case) + `ReservationRepository` (port) + test
3. **Adapter In**: `ReservationResource.java` (REST resource) + DTOs + `@QuarkusTest`
4. **Adapter Out**: `PanacheReservationRepository.java` (implements port) + `@QuarkusTest`

### Run a Single Test

```bash
# Domain test (fast, no Quarkus)
./mvnw -pl inventory-service test -Dtest=VehicleTest

# Application test (fast, no Quarkus)
./mvnw -pl inventory-service test -Dtest=RegisterVehicleTest

# Adapter test (QuarkusTest, slower)
./mvnw -pl inventory-service test -Dtest=GraphQLInventoryResourceTest

# Integration test (native/package)
./mvnw -pl inventory-service verify -Pnative -DskipITs=false
```

---

## 7. Key Files to Know

| Purpose | File |
|---------|------|
| Engineering rules | `AGENTS.md` |
| Domain model | `docs/domain.md` |
| Architecture | `docs/architecture.md` |
| Service details | `docs/services.md` |
| Testing strategy | `docs/testing.md` |
| Event contracts | `docs/contracts.md` |
| Roadmap | `docs/roadmap.md` |
| DDD/TDD standards | `docs/ddd-tdd-standards.md` |
| ADRs | `docs/adr/README.md` |

---

## 8. OpenCode Agents (Architecture Gates)

| Agent | Purpose | Command |
|-------|---------|---------|
| `domain-designer` | Design domain before coding | `/domain-design <feature>` |
| `architecture-guardian` | Cross-service consistency | `/architecture-audit <scope>` |
| `ddd-guardian` | DDD boundaries | `/ddd-audit <service>` |
| `tdd-guardian` | TDD evidence | `/tdd-audit <service>` |
| `quarkus-book-guardian` | Quarkus 3.39.3 alignment | `/quarkus-audit <service>` |
| `feature-implementer` | Default agent (implements features) | Default in `opencode.json` |

**Before any significant change:**
```bash
/preflight "my-feature-name"
```

**After implementation:**
```bash
/ddd-audit inventory-service
/tdd-audit inventory-service
/quarkus-audit inventory-service
```

---

## 9. Debugging Tips

| Issue | Solution |
|-------|----------|
| Port conflicts | Dev mode uses random test ports (`quarkus.http.test-port=0`). Don't hardcode ports. |
| Kafka not connecting | Check `%dev.quarkus.kafka.devservices.port=39092` and `shared=true` |
| MySQL Dev Service fails | Ensure Docker daemon running; check `33306` not in use |
| GraphQL schema not updating | Restart dev mode; check `quarkus.smallrye-graphql.ui.always-include=true` |
| gRPC reflection | Enabled via `quarkus.grpc.server.enable-reflection-service=true` |
| Test deadlock (reactive) | Never use `await().indefinitely()` on Vert.x context; use `UniAsserter` |

---

## 10. Project-Specific Conventions

| Convention | Example |
|------------|---------|
| **Test naming** | `shouldRejectReservationWhenVehicleAlreadyReserved` |
| **Domain commands** | `vehicle.decommission()`, `reservation.confirm()` (no setters) |
| **Value objects** | `LicensePlate`, `VehicleDailyRate`, `Money`, `RentalPeriod` |
| **Output ports** | `InventoryGateway`, `RentalGateway`, `InvoiceRepository` |
| **Anti-corruption** | Each context has its own `VehicleId`, `CustomerId`, `ReservationId` |
| **Commits** | `test: ...`, `feat: ...`, `refactor: ...` (keep behavioral changes separate) |

---

## 11. Useful Commands Cheatsheet

```bash
# All tests
./mvnw test

# Single module
./mvnw -pl inventory-service test

# With dependencies
./mvnw -pl reservation-service -am test

# Dev mode (all services via compose)
cd others && docker-compose up

# Clean build
./mvnw clean install -DskipTests

# Native image (slow)
./mvnw -pl inventory-service package -Pnative

# Check for dependency updates
./mvnw versions:display-dependency-updates
```

---

## 12. When Stuck

1. Check `docs/knowledge/11-armadilhas-e-licoes.md` — real traps already hit
2. Check `docs/adr/` — architectural decisions with rationale
3. Run guardians: `/ddd-audit <service>` or `/tdd-audit <service>`
4. Look at existing tests in the same layer for patterns
5. Ask: "Where is the domain behavior?" → it's in the aggregate, not the resource/repository

---

## 13. Status Legend (used across all docs)

| Symbol | Meaning |
|--------|---------|
| ✅ | Implemented + validated |
| ⚠️ | Partial / known issue |
| 🚧 | Placeholder (structure ready) |
| 🔜 | Planned (not started) |

---

_Última atualização: 2026-09-27_