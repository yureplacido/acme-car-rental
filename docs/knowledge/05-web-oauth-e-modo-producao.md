# 05 — Web, segurança OIDC e modo produção

> Capítulo 6 do *Quarkus in Action*.
> Fato operacional em [deployment.md](../deployment.md) e [services.md](../services.md).

## 1. O que o livro ensina

O capítulo 6 junta três assuntos que na prática andam juntos:

1. **expor uma aplicação web** (Qute + HTMX, sem build de frontend);
2. **proteger** a aplicação (OIDC contra um Keycloak);
3. **rodar como em produção** (`quarkus:build` + container), porque dev mode e produção
   **não** são o mesmo runtime.

## 2. Dev mode ≠ produção

O erro mais caro de quem só conhece dev mode:

| | dev mode | produção |
|---|---|---|
| live reload | sim | não |
| Dev Services | sobe Postgres/MySQL/MariaDB/Mongo/Keycloak (**não** Kafka) | ❌ você configura |
| logging | legível, colorido | formato de produção |
| Dev UI | exposta | não |
| management interface (`quarkus.management.enabled`, default `false`, `rootPath=/q`) | opcional | opcional |
| augmentation | simplificada | completa |

Regra do projeto: **toda configuração de produção existe em `%prod` ou `%docker`**, nunca
"em algum lugar". Ver [01-fundamentos-e-dev-mode.md](./01-fundamentos-e-dev-mode.md) §1.3.

## 3. Segurança OIDC

O Quarkus delega autenticação ao Keycloak via `quarkus-oidc`. O projeto usa **dois
perfis de aplicação**, porque são problemas diferentes:

### 3.1 Serviço (Bearer / machine-to-machine)

```properties
# reservation-service
quarkus.oidc.application-type=service
```

Sem `permission.*` obrigatório porque **acesso anônimo** é permitido (chamada sem header
`Authorization` não é rejeitada). O serviço ainda valida token quando ele vem.

### 3.2 Aplicação web (Authorization Code Flow)

```properties
# users-service
quarkus.oidc.application-type=web_app
quarkus.oidc.logout.path=/logout
quarkus.http.auth.permission.all-resources.paths=/*
quarkus.http.auth.permission.all-resources.policy=authenticated
quarkus.http.auth.permission.openapi.paths=/users/q/openapi
quarkus.http.auth.permission.openapi.policy=permit
```

Ponto de atenção real deste arquivo: **o caminho específico vence o `/*`**. Foi assim que o
OpenAPI do agregador ficou acessível sem login, sem abrir o resto.

⚠️ **Armadilha:** `quarkus.http.auth.permission.<nome>.paths` é uma lista
separada por `,` e o *match* é por caminho. Um `permit` amplo demais vira backdoor silencioso.

## 4. Token propagation

Quando o `users-service` (web) chama o `reservation-service` (serviço), o token do usuário
precisa atravessar a fronteira. No projeto isso aparece como **porta de saída** com anotações
do client no adapter — nunca no caso de uso:

```text
users-service (web_app, token do usuário)
    ↓ adapter REST: propaga Authorization
reservation-service (service, valida o token)
```

Se a propagação sumir, o `reservation-service` responde 401 e o sintoma parece "o endpoint
está quebrado".

## 5. Qute + HTMX

O capítulo 6.2 mostra a UI de gerenciamento de reservas: template server-side (Qute) + um
pouco de JS via HTMX, sem npm, sem bundler. Vantagem no contexto do projeto: a
`users-service` é **BFF**, então o servidor já tem sessão/token — a UI é consequência
direta disso, não um detalhe de frontend.

## 6. Modo produção (cap. 6.4)

```bash
# ⚠️ o goal 'quarkus:prod' NÃO existe no 3.39.3 (verificado no plugin.xml).
# O caminho é build do pacote de produção e execução do jar.
./mvnw -pl users-service package
java -jar users-service/target/quarkus-app/quarkus-run.jar
```

Goals existentes em `quarkus-maven-plugin` 3.39.3: `dev`, `build`, `run`, `test`, `package`,
`deploy`, `generate-code`, … — `prod` foi removido em versões anteriores. Quem seguir a
receita antiga recebe `Could not find goal 'prod'`.

Com `%prod`, o Keycloak **não** é mais Dev Service: o projeto sobe Keycloak via
`others/docker-compose.yml` com realm `car-rental` e clients `users-service` /
`reservation-service`. Detalhes de porta e realm em [deployment.md](../deployment.md).

## 7. O que o projeto acrescenta

### 7.1 `root-path` como decisão de gateway

```properties
# users-service
quarkus.http.root-path=/users
```

O OpenAPI passa a ser `/users/q/openapi`, casa com o prefixo do Traefik no agregador e
**não** fica dentro da regra de autenticação — o que obrigaria o browser a autenticar
só para abrir o Swagger.

### 7.2 `%docker` como terceiro ambiente

`%prod` = `java -jar` na máquina. `%docker` = container na rede do compose, onde o host do
Keycloak é `keycloak`, não `localhost`. São dois perfis porque são dois **sistemas de
resolução de nomes** diferentes:

```properties
%prod.quarkus.oidc.auth-server-url=http://localhost:${KEYCLOAK_PORT:7777}/realms/car-rental
%docker.quarkus.oidc.auth-server-url=http://keycloak:8080/realms/car-rental
```

## 8. Checklist de estudo

- [ ] Sei listar 4 diferenças entre dev mode e produção.
- [ ] Sei explicar `application-type=service` vs `web_app`.
- [ ] Sei explicar por que `permission` de path específico vence `/*`.
- [ ] Sei dizer o que acontece se a propagação de token for removida.
- [ ] Sei rodar um serviço em `%prod` e apontar para o Keycloak do compose.
- [ ] Sei dizer por que `root-path=/users` ajuda o agregador.

**Veja também:** [01-fundamentos-e-dev-mode.md](./01-fundamentos-e-dev-mode.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md)

---

_Última atualização: 2026-09-26 (cap. 6)._
