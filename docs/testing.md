# Testes

> **Última atualização:** 2026-09-19 (caps.5-6) · **Fonte da verdade:** o código.

Estratégia do cap.5 do livro, aplicada hoje no **reservation-service** (demais serviços
ainda sem testes). Duas camadas:

- **JVM** — `@QuarkusTest` (mesmo JVM, injeção CDI e mocks disponíveis) → surefire (`test`).
- **Nativo/integration** — `@QuarkusIntegrationTest` (roda contra o artefato
  pré-compilado: JAR/container/binário nativo) → failsafe, classe com sufixo `IT`
  (padrão do livro) e `skipITs=true` por default.

## Árvore de testes (reservation-service)

`src/test/java/org/acme/reservation/`

| Classe | Foco (cap.) | Modo | Detalhe |
|---|---|---|---|
| `ReservationRepositoryTest` | White-box do repositório (5.1.1) | JVM | Injeta `ReservationsRepository` (in-memory), `save()` atribui id |
| `ReservationResourceTest` | Black-box REST (5.2.1) + Mockito (5.3.2) | JVM | `@TestHTTPEndpoint/@TestHTTPResource`, RestAssured, `QuarkusMock.installMockForType` |
| `ReservationResourceIT` | Nativo (5.2.2) | Nativo | Herda `ReservationResourceTest`; roda só no perfil nativo |
| `StagingTest` + `RunWithStaging` | Testing profiles (5.4) | JVM | `@TestProfile(RunWithStaging.class)` sobrescreve config + tag `staging` |

### Métodos implementados

- `testCreateReservation()` — salva reserva futura (carId arbitrário), verifica id não nulo e contida em `findAll()`.
- `testReservationIds()` — POST `/reservations` → 200 + `id` notNullValue.
- `testMakingAReservationAndCheckAvailability()` — mocks do `GraphQLInventoryClient`
  (Mockito), GET availability → reserva → carro SOME da lista (`hasSize(0)`).
  Marcos `@DisabledOnIntegrationTest(NATIVE_BINARY)` (mock não roda em nativo).
- `testStagingProfileOverridesGraphQLUrl()` — valida override do perfil.

## Config de teste (por que 8181)

```properties
# reservation-service/src/main/resources/application.properties
quarkus.http.test-port=8181
```

O dev roda em **8081** (`RESERVATION_PORT`) e a instância de `@QuarkusTest` **por padrão
usaria a mesma porta** → clash no continuous testing. O livro manda separar (8181) ou usar `0`
(porta livre automática).

## Dependências de teste (reservation pom, scope test)

```xml
io.quarkus:quarkus-junit                 <!— framework de teste do Quarkus
io.rest-assured:rest-assured             <!— testes REST (cap.5.2)
io.quarkus:quarkus-junit-mockito         <!— Mockito + QuarkusMock (cap.5.3.2)
```

> ⚠️ A partir do Quarkus **3.31** o artefato `quarkus-junit5-mockito` foi **relocado**
> para `quarkus-junit-mockito`. Use o novo nome.

## Comandos

| Comando | Executa |
|---|---|
| `./mvnw test` | Só testes JVM (`@QuarkusTest`) — as `IT` são ignoradas |
| `./mvnw verify` | JVM + empacota; failsafe pula ITs (`skipITs=true`) |
| `./mvnw verify -Dnative` | Compila binário nativo e roda as `IT` (requer GraalVM / `native-image`) |
| `./mvnw test -Dquarkus.test.profile.tags=staging` | Roda só os testes cujo perfil tem a tag `staging` |
| Dev mode → pressionar `r` | Continuous testing |

### Filtro por tags (cap.5.4)

```bash
./mvnw verify -Dquarkus.test.profile.tags=staging   # só StagingTest roda
./mvnw verify -Dquarkus.test.profile.tags=outra      # nada roda (todos pulados)
```

As tags vêm do perfil (`RunWithStaging.tags()` = `{"staging"}`); testes sem perfil/tag
são pulados quando um filtro de tag existe.

## Limitações conhecidas (livro)

- Injeção de CDI **no próprio teste** e mocks **não funcionam em nativo** (processos
  separados). Testes que dependem disso devem ser marcados `@DisabledOnIntegrationTest`
  ou ficarem só em classes `@QuarkusTest`.
- `@QuarkusTest` injeta o mock via flag/instalação **antes** de qualquer chamada HTTP
  (ex.: `QuarkusMock.installMockForType` logo no início do método).

## OIDC e testes (cap.6)

- Com `quarkus-oidc` no classpath, o `@QuarkusTest` do reservation-service sobe um
  **Keycloak Dev Services** (container) — agora os testes do reservation **exigem
  Docker**. Sem Docker, use `quarkus.oidc.tenant-enabled=false` num perfil de teste.
- A validação do cap.6.2/6.4 foi feita por **E2E manual** (curl através do fluxo
  Authorization Code: login `alice` → `/`, `/available`, `/reserve` → confere
  `userId=alice` em `/reservations/all`). Dev/prod passam no mesmo roteiro.

---

_🔜 Quando novos serviços ganharem testes (caps. futuros), registre as classes aqui e no
[roadmap.md](./roadmap.md)._