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
- [ ] **Cap. 6 — Exposing e securing web apps** 🔜
  - [ ] Exposição web/estática (Qute já no users-service)
  - [ ] **Segurança OIDC** no users-service (`quarkus-oidc`, token propagation já previsto)
  - [ ] Proteção dos endpoints dos serviços + testes de autenticação
  - [ ] Atualizar: `services.md` (users/security), `deployment.md`, `testing.md`
- [ ] **Cap. 7 — Database access** 🔜
  - [ ] inventory → **MySQL** (datasource + Panache), trocar `app.repository`
  - [ ] reservation → **PostgreSQL reativo** (Panache reativo)
  - [ ] rental → ativar **MongoDB** (`MongoRentalRepository` já existe)
  - [ ] billing → MongoDB Panache (deps já no pom)
  - [ ] Atualizar: `services.md`, `contracts.md`, `deployment.md`
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

- [ ] Alinhar `swagger/index.html` → `/reservations/q/openapi` (hoje singular `/reservation`)
- [ ] Testes nos demais serviços (rental, inventory, users, billing)
- [ ] Subir stack toda via `docker compose up --profile docker` e validar fluxo fim-a-fim
  no gateway (ver [deployment.md](./deployment.md))
- [ ] Committar o **cap.5** (testes) e esta documentação

---

_Próximo: **cap.6 — Exposing e securing web applications** (p.143 do livro)._