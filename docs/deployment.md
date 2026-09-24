# Deploy / Ambiente

> **Última atualização:** 2026-09-20 (cap.7 - perfis do compose) · **Fonte da verdade:** o código.

Três modos de execução:

1. **Dev local** — cada serviço pelo `./mvnw quarkus:dev` (JVM no host), portas diretas.
   Com `quarkus-oidc`, o **Dev Services keycloak** sobe sozinho (usuários `alice`/`bob`).
   Bancos são **Dev Services** (Postgres/MySQL/Mongo zerados em container) — ver cap.7.
2. **Docker (compose)** — subir a stack (ou partes dela) via **perfis**; edge
   (Traefik/Swagger) sobe por padrão via `COMPOSE_PROFILES=infra` no `.env`.

   Os assets não-Java vivem em **`others/`** (compose, `.env`, `keycloak/`, `swagger/`,
   `traefik/`). Rode os comandos a partir de `others/`:
   `cd others && docker compose up ...`
3. **Produção manual (cap.6.4)** — Keycloak + PostgreSQL no compose e os serviços
   empacotados rodando via `java -jar` no host (ou containers com `QUARKUS_PROFILE=docker`).

## Docker (`others/docker-compose.yml`)

Serviços no compose: `traefik`, `swagger`, `users-service`, `reservation-service`,
`rental-service`, `inventory-service`, `billing-service` + bancos do cap.7
(`reservation-postgres`, `inventory-mysql`, `rental-mongo`) + **`keycloak`**, **`postgres`**
(cap.6.4) + mensageria do cap.9: **`kafka`** (broker KRaft `apache/kafka:3.9.1`) e
**`kafka-init`** (provisiona os tópicos `vehicle-registered`, os retry
`vehicle-registered-retry_1000/5000/15000` e a DLQ `vehicle-registered-dlq` antes de
`inventory-service`/`billing-service` via
`depends_on: service_completed_successfully`; `KAFKA_AUTO_CREATE_TOPICS_ENABLE=false`).
Os serviços de messaging usam o perfil `QUARKUS_PROFILE=docker` com
`%docker.kafka.bootstrap.servers=kafka:9092`.

### Perfis

Cada serviço pertence ao seu grupo **e** ao perfil `all`. Definido em `others/.env`:
`COMPOSE_PROFILES=infra` torna o `docker compose up` **sem flags** = só o agregador.

> Todos os comandos abaixo partem de `others/` (`cd others`).

| Perfil | Serviços | Comando |
|---|---|---|
| `infra` | traefik + swagger | `docker compose up -d` |
| `services` | 5 aplicações + seus 3 bancos | `docker compose up -d --profile services` |
| `databases` | só os 3 bancos dos serviços | `docker compose up -d --profile databases` |
| `identity` | keycloak + postgres | `docker compose up -d --profile identity` |
| `all` | tudo | `docker compose up -d --profile all` |

- Cada serviço recebe `QUARKUS_PROFILE=docker`, ativando os overrides `%docker.` no
  `application.properties` (ex.: reservation aponta para `http://rental-service:8082`
  e `http://inventory-service:8083/graphql` — **nomes de container**, não `localhost`).
- `extra_hosts: host.docker.internal:host-gateway` permite o **Traefik** alcançar
  serviços que rodam no host (dev sem Docker) — por isso dá para subir **só o agregador**
  no compose e as aplicações no **IntelliJ** (dev mode), desde que as portas batam com o `others/.env`.
- Bancos têm `healthcheck`; aplicações usam `depends_on: condition: service_healthy`.
- Portas publicadas via env do `others/.env`.

**Subir tudo:**

```bash
cd others
docker compose up -d --profile all
```

**Só a edge / agregador (aplicações via IntelliJ, por exemplo):**

```bash
cd others
docker compose up -d
```

## Keycloak + PostgreSQL (produção — cap.6.4)

Serviços `keycloak` (quay.io/keycloak/keycloak:25.0.6) e `postgres` (postgres:14) sob o
perfil `identity`, com **realm importado no boot**:

```bash
cd others
docker compose up -d --profile identity
```

- Realm **`car-rental`** (`others/keycloak/car-rental-realm.json`) com clients
  `users-service` e `reservation-service` (públicos, redirect `*`) e usuários
  `alice`/`bob` (senha = usuário).
- **Porta do host:** `${KEYCLOAK_PORT:-7777}` → 8080 interno. PostgreSQL **não é
  exposto** — fala só com o Keycloak.
- Keycloak admin: `admin/admin`. Dados vão para o PostgreSQL (`KC_DB_*`).
- ⚠️ No primeiro boot o PostgreSQL pode demorar e o Keycloak reiniciar (esperado).

### Rodando os serviços em produção (sem containers)

```bash
./mvnw clean package          # em users-service e reservation-service
java -jar target/quarkus-app/quarkus-run.jar   # cada um na sua pasta
# inventory-service pode rodar dev/prod (não usa OIDC)
```

Os `%prod.*` no `application.properties` apontam para
`http://localhost:7777/realms/car-rental` (compose local). Para rodar **dentro** do
compose, os serviços usam `%docker.*` → `http://keycloak:8080/realms/car-rental`
(porta interna). A UI fica em `http://localhost:8080`.

| Modo | Quem gerencia o OIDC | Config usada |
|---|---|---|
| Dev | Dev Services (automático, Keycloak 26) | padrão |
| Prod JVM | compose (`keycloak:25` + `postgres`) | `%prod.*` → localhost:7777 |
| Docker compose | mesmo compose, via rede interna | `%docker.*` → keycloak:8080 |

## Edge (Traefik + Swagger)

**Traefik v3** — gateway único no host portas `8090` (web) e `8095` (dashboard).
Config dinâmica em `others/traefik/dynamic.yml`.

| Rota (PathPrefix) | Serviço de destino | Prioridade |
|---|---|---|
| `/` | Swagger UI agregado (`http://acme-swagger:80`) | 1 (fallback) |
| `/users` | `host.docker.internal:${USER_SERVICE_PORT}` (8080) | 100 |
| `/reservations` | `host.docker.internal:${RESERVATION_PORT}` (8081) | 100 |
| `/rental` | `host.docker.internal:${RENTAL_PORT}` (8082) | 100 |
| `/billing` | `host.docker.internal:${BILLING_PORT}` (8084) | 100 |
| `/graphql` | `host.docker.internal:${INVENTORY_PORT}` (8083) | 100 |

**Swagger agregado** — container `nginx:alpine` servindo `others/swagger/index.html` (Swagger UI
bundled) que consolida os OpenAPI dos serviços. Como o traefik encaminha **sem strip**,
cada serviço expõe o documento no próprio prefixo do gateway (`others/swagger/index.html` e
`application.properties` batem):

- users/openapi → `/users/q/openapi` (derivado de `quarkus.http.root-path=/users`)
- reservation → `/reservations/q/openapi` (`quarkus.smallrye-openapi.path`)
- rental → `/rental/q/openapi` (`quarkus.smallrye-openapi.path`)
- billing → `/billing/q/openapi` (`quarkus.smallrye-openapi.path`)

### Observações / limitações conhecidas

- ✅ O entry do aggregator agora é `/reservations/q/openapi` (plural), alinhado com a rota
  do traefik e com o path do serviço.
- ✅ O CRUD REST Data do cap.7 (reativo) fica em **`/reservations/admin/reservation`**
  (path com prefixo no `@ResourceProperties`), visível no OpenAPI do reservation.
- ⚠️ **inventory não tem REST** → não aparece no agregador (GraphQL/gRPC only).
- ⚠️ **gRPC não passa pelo gateway** — o CLI conecta direto em `localhost:9000`.
- A rota `/graphql` só cobre o **GraphQL UI/endpoint** do inventory, não um proxy geral.
- ⚠️ **users-service exige login** nas rotas da UI (`302 → keycloak`); só o `/users/q/openapi`
  está liberado (permission `permit` específica).

## Variáveis de ambiente (`others/.env`)

| Chave | Default | Uso |
|---|---|---|
| `USER_SERVICE_PORT` | 8080 | Porta users-service |
| `RESERVATION_PORT` | 8081 | Porta reservation-service |
| `RENTAL_PORT` | 8082 | Porta rental-service |
| `INVENTORY_PORT` | 8083 | Porta inventory-service |
| `BILLING_PORT` | 8084 | Porta billing-service |
| `GATEWAY_PORT` | 8090 | Porta web do Traefik |
| `DASHBOARD_PORT` | 8095 | Porta do dashboard do Traefik |
| `KEYCLOAK_PORT` | 7777 | Porta do Keycloak (produção, realm car-rental) |
| `COMPOSE_PROFILES` | `infra` | Perfil ativo por padrão no `docker compose up` |

Serviços também leem os mesmos `${NOME}` nos `application.properties` (overrides i.e.
`RENTAL_SERVICE_URL`, `INVENTORY_SERVICE_URL`).

## Build das imagens

Cada serviço tem `Dockerfile` (multi-stage):

1. `maven:3.9-eclipse-temurin-21` compila (`mvn package -DskipTests`);
2. `eclipse-temurin:21-jre` roda `quarkus-run.jar`.

⚠️ O **inventory-service** usa contexto de build = **raiz do repositório**
(`context: ..` + `dockerfile: inventory-service/Dockerfile` no `others/docker-compose.yml`),
pois seu Dockerfile compila o contrato standalone `inventory-proto` antes do serviço
(`.dockerignore` na raiz restringe o contexto a `inventory-proto/` + `inventory-service/`).

---

_Caps. 10-12 tratarão cloud — atualize este arquivo e [roadmap.md](./roadmap.md)._
_⚠️ A rota `/users` do Traefik atende a UI do cap.6; o login OIDC exige cookies de
sessão — as URLs de redirect (localhost:8080) devem bater com o client configurado
(Dev Services faz isso sozinho; o realm manual usa redirect `*`)._