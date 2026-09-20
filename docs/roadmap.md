# Roadmap — por capítulo do livro

> **Última atualização:** 2026-09-19 · Cada capítulo é um checkbox — marque ao completar
> e atualize as páginas indicadas (fonte da verdade: [README.md](./README.md)).

Legenda: **[ ]** pendente · **[x]** feito.

## Part 1 — Getting started

- [x] **Cap. 1-2** — O que é Quarkus; primeira aplicação. → `services.md`
- [x] **Cap. 3** — Produtividade no dev (dev mode, continuous testing). → `services.md`, `testing.md`

## Part 2 — Desenvolvendo aplicações

- [x] **Cap. 4 — Communications** (REST, GraphQL, gRPC) ✅
  - [x] REST + Swagger UI (reservation/rental)
  - [x] REST client (`@RestClient`)
  - [x] GraphQL server (code-first) + clientes (tipado + dinâmico + paginação/busca/sort)
  - [x] gRPC: contrato standalone (`inventory-proto`), unary + streaming bidirecional, CLI
- [x] **Cap. 5 — Testing** ✅
  - [x] `@QuarkusTest` white/black-box, RestAssured, `@TestHTTPEndpoint/@TestHTTPResource`
  - [x] `@QuarkusIntegrationTest` (nativo) + `IT`/`skipITs`
  - [x] Mockito + `QuarkusMock`; testes de perfis (`@TestProfile`, tags)
  - [ ] ☐ Reserva: rodar um `verify -Dnative` de verdade (requer GraalVM) — infra já pronta
- [x] **Cap. 6 — Exposing e securing web apps** ✅
  - [x] users-service: página `/` + `/whoami` (Qute) e `/logout`
  - [x] **Segurança OIDC** (Keycloak): users `web_app` (login obrigatório),
    reservation `service` (Bearer opcional, grava `userId`)
  - [x] Propagação do token (`@AccessToken` no `ReservationsClient`) + UI HTMX
    (`/get`, `/available`, `/reserve`) — validado E2E em dev e produção
  - [x] Produção: Keycloak+PostgreSQL no compose (realm `car-rental`) + wiring
    `%prod`/`%docker`
  - [x] Atualizado: `services.md` (users/security), `deployment.md`, `testing.md`
- [x] **Cap. 7 — Database access** ✅ (inventory/reservation/rental)
  - [x] inventory → **MySQL** (Panache repository + `import.sql` seed, `sql-load-script` p/ prod/docker)
  - [x] reservation → **PostgreSQL reativo** (Panache reativo, `@WithTransaction`/`Uni`) + CRUD REST Data em `/reservations/admin/reservation`
  - [x] rental → **MongoDB** (Panache active record, `PanacheMongoEntity`)
  - [x] **Split Model/Entity** nos 3 serviços: `model/*` POJO (Lombok, GraphQL no `Car`)
    × `entity/*Entity` (Panache, campos públicos), `*Mapper` + `XRepository` (interface
    + impl `Panache*Repository`) como seam Active Record ↔ Repository; REST Data CRUD
    admin segue na entidade (reservation)
  - [ ] billing → MongoDB Panache (deps já no pom) 🔜
  - [x] Atualizado: `services.md`, `deployment.md` (perfis do compose), `architecture.md` (ADR 9)
- [ ] **Cap. 8 — Reactive programming** 🔜
  - [ ] Ponto reativo em serviços chave (Mutiny já usado no gRPC); deixar de bloqueante
  - [ ] Atualizar: `architecture.md`, `services.md`
- [ ] **Cap. 9 — Quarkus messaging** 🔜
  - [ ] billing recebe/emite eventos (RabbitMQ ou Kafka — deps já no pom)
  - [ ] Definir contrato de eventos em `contracts.md`
  - [ ] Atualizar: `services.md` (billing), `contracts.md`

## Part 3 — Cloud e além

- [ ] **Cap. 10-12** — Cloud-native patterns, deploy em cloud, extensões custom 🔜
  - [ ] Conteinerização/registry, Kubernetes/OpenShift, observabilidade
  - [ ] Atualizar: `deployment.md`, `architecture.md`

## Pendências transversais

- [x] Alinhar `swagger/index.html` → `/reservations/q/openapi` (era singular `/reservation`);
  cada serviço expõe o OpenAPI no prefixo do gateway (reservation/rental/billing via
  `quarkus.smallrye-openapi.path`, users via `root-path=/users`) — validado E2E no agregador
- [ ] Testes nos demais serviços (rental, inventory, users, billing)
- [x] Subir stack toda via `docker compose up -d --profile all` (perfis infra/services/
  databases/identity; `.env` com `COMPOSE_PROFILES=infra`) e validar fluxo fim-a-fim no
  gateway (ver [deployment.md](./deployment.md))

---

_Próximo: **cap.7 — billing (MongoDB Panache)**, depois **cap.8 — Reactive programming** (p.171 do livro)._