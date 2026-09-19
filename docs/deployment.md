# Deploy / Ambiente

> **Última atualização:** 2026-09-19 (base cap.1-5) · **Fonte da verdade:** o código.

Dois modos de execução:

1. **Dev local** — cada serviço pelo `./mvnw quarkus:dev` (JVM no host), portas diretas.
2. **Docker (compose)** — subir a stack com o perfil `docker` (services isolados) + edge
   (Traefik/Swagger).

## Docker (docker-compose.yml)

Serviços no compose: `traefik`, `swagger`, `users-service`, `reservation-service`,
`rental-service`, `inventory-service`, `billing-service`.

- Os **serviços Quarkus** ficam sob `profiles: ["docker"]` — só sobem com
  `--profile docker` (evita consumir recursos no uso só do edge).
- Cada serviço recebe `QUARKUS_PROFILE=docker`, ativando os overrides `%docker.` no
  `application.properties` (ex.: reservation aponta para `http://rental-service:8082`
  e `http://inventory-service:8083/graphql` — **nomes de container**, não `localhost`).
- `extra_hosts: host.docker.internal:host-gateway` permite o **Traefik** alcançar
  serviços que rodam no host (dev sem Docker).
- Portas publicadas via env do `.env`.

**Subir tudo:**

```
docker compose up -d --profile docker
```

**Só a edge (navegação/doc):**

```
docker compose up -d
```

## Edge (Traefik + Swagger)

**Traefik v3** — gateway único no host portas `8090` (web) e `8095` (dashboard).
Config dinâmica em `traefik/dynamic.yml`.

| Rota (PathPrefix) | Serviço de destino | Prioridade |
|---|---|---|
| `/` | Swagger UI agregado (`http://acme-swagger:80`) | 1 (fallback) |
| `/users` | `host.docker.internal:${USER_SERVICE_PORT}` (8080) | 100 |
| `/reservations` | `host.docker.internal:${RESERVATION_PORT}` (8081) | 100 |
| `/rental` | `host.docker.internal:${RENTAL_PORT}` (8082) | 100 |
| `/billing` | `host.docker.internal:${BILLING_PORT}` (8084) | 100 |
| `/graphql` | `host.docker.internal:${INVENTORY_PORT}` (8083) | 100 |

**Swagger agregado** — container `nginx:alpine` servindo `swagger/index.html` (Swagger UI
bundled) que consolida os OpenAPI dos serviços (`/q/openapi`).

### Observações / limitações conhecidas

- ⚠️ O `swagger/index.html` referencia `/reservation/q/openapi` (singular), mas a rota e o
  path da aplicação são `/reservations` (plural) → o botão "Try it" do reservation no
  agregador **ficaria 404**; falta alinhar o entry para `/reservations/q/openapi`.
- ⚠️ **inventory não tem REST** → não aparece no agregador (GraphQL/gRPC only).
- ⚠️ **gRPC não passa pelo gateway** — o CLI conecta direto em `localhost:9000`.
- A rota `/graphql` só cobre o **GraphQL UI/endpoint** do inventory, não um proxy geral.

## Variáveis de ambiente (`.env`)

| Chave | Default | Uso |
|---|---|---|
| `USER_SERVICE_PORT` | 8080 | Porta users-service |
| `RESERVATION_PORT` | 8081 | Porta reservation-service |
| `RENTAL_PORT` | 8082 | Porta rental-service |
| `INVENTORY_PORT` | 8083 | Porta inventory-service |
| `BILLING_PORT` | 8084 | Porta billing-service |
| `GATEWAY_PORT` | 8090 | Porta web do Traefik |
| `DASHBOARD_PORT` | 8095 | Porta do dashboard do Traefik |

Serviços também leem os mesmos `${NOME}` nos `application.properties` (overrides i.e.
`RENTAL_SERVICE_URL`, `INVENTORY_SERVICE_URL`).

## Build das imagens

Cada serviço tem `Dockerfile` (multi-stage):

1. `maven:3.9-eclipse-temurin-21` compila (`mvn package -DskipTests`);
2. `eclipse-temurin:21-jre` roda `quarkus-run.jar`.

---

_🔜 cap.6 adicionará exposição/segurança (OIDC); caps. 10-12 tratarão cloud — atualize
este arquivo e [roadmap.md](./roadmap.md)._