# 03 — Testes Quarkus (o framework do livro)

> Capítulo 5 do *Quarkus in Action*.
> Este documento descreve **o framework**. O **padrão do projeto** está em
> [04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) — leia os dois.

## 1. O framework em uma frase

O Quarkus Testing é JUnit 5 com um **extensão que sobe a aplicação de verdade** e oferece
instalações que o JUnit puro não tem: injetar beans no teste, injetar a URL do endpoint,
trocar beans por mocks, aplicar perfis e subir recursos externos.

```text
JunitTest
   ├── @QuarkusTest            → sobe a aplicação e injeta beans/URLs (JVM)
   └── @QuarkusIntegrationTest → roda contra o artefato empacotado (JVM ou nativo)
```

## 2. As seis instalações (cap. 5.1)

1. **Injetar beans CDI** no teste (`@Inject`).
2. **Injetar URLs** de endpoints (`@TestHTTPEndpoint` / `@TestHTTPResource`).
3. **Integrar com Mockito** (`QuarkusMock`).
4. **Substituir beans** por outra implementação (`@Mock`).
5. **Perfis de teste** (`@TestProfile`) — mesma app, configuração diferente.
6. **Subir serviços** que a app precisa (`QuarkusTestResourceLifecycleManager`).

> ⚠️ **Limitação do modo nativo:** 1, 3 e 4 **não funcionam** em nativo. O teste nativo roda
> em uma JVM separada do binário, se comunicando por HTTP. Por isso o acesso é por
> endpoint, nunca por injeção.

## 3. `@QuarkusTest`: white-box × black-box

O livro contrasta dois níveis, e essa distinção é a base do nosso padrão de camadas:

```java
// white-box: injeta o bean direto. Não roda em nativo.
@QuarkusTest
class ReservationRepositoryTest {
    @Inject ReservationsRepository repository;
}

// black-box: passa pelo HTTP. Roda em JVM e nativo.
@QuarkusTest
class ReservationResourceTest {
    @TestHTTPEndpoint(ReservationResource.class) @TestHTTPResource URL url;
}
```

🧪 **No repositório**, o white-box de bean **não** é usado para provar persistência. Ele é
usado para provar **contratos de adapter** (ver [04](./04-estrategia-de-testes-do-projeto.md)).

## 4. RestAssured (cap. 5.2)

O framework já gerencia a URL base, então o teste não monta URL:

```java
given()
    .contentType(ContentType.JSON)
    .body("""
        { "carId": 9001, "startDay": "2035-06-01", "endDay": "2035-06-10" }
        """)
    .when().post("/reservations")
    .then().statusCode(200)
    .body("id", notNullValue())
    .body("status", equalTo("PENDING"));
```

Fonte: `reservation-service/src/test/java/org/acme/reservation/adapter/in/rest/ReservationResourceTest.java`

Dependência: `io.rest-assured:rest-assured` com `<scope>test</scope>`.

## 5. Teste nativo e integração (cap. 5.2)

O padrão recomendado é **herdar**:

```java
@QuarkusIntegrationTest
class ReservationResourceIT extends ReservationResourceTest {}
```

Se um método não puder rodar em nativo, ele é desligado explicitamente:

```java
@DisabledOnIntegrationTest(forArtifactTypes = DisabledOnIntegrationTest.ArtifactType.NATIVE_BINARY)
@Test
void testUsingMocks() { ... }
```

Execução:

| Comando | O que roda |
|---|---|
| `./mvnw test` | só testes JVM (`@QuarkusTest`) |
| `./mvnw verify -Pnative` | compila o binário e roda os `IT` |

🔀 **Estado no projeto:** native build e testes de integração estão em
[roadmap.md](../roadmap.md) como **não concluídos** (`Part 3`). Documentamos a teoria e
o padrão agora para não descobrir o problema no fim.

## 6. Mocking: duas técnicas (cap. 5.3)

### 6.1 Substituindo a implementação do bean

```java
@Mock  // = @Alternative + @Priority(1) + @Dependent
public class MockInventoryClient implements GraphQLInventoryClient { ... }
```

Basta estar em `src/test/java` para ser descoberto. Serve para **qualquer** implementação
da interface, inclusive a gerada.

⚠️ O livro avisa (e o projeto confirmou na prática): **não misture** `@Mock` de bean com
`QuarkusMock` para a mesma interface — o conflito é silencioso e difícil de ver.

### 6.2 Mockito

```java
GraphQLInventoryClient mock = Mockito.mock(GraphQLInventoryClient.class);
Mockito.when(mock.allCars()).thenReturn(List.of(peugeot));
QuarkusMock.installMockForType(mock, GraphQLInventoryClient.class);
```

Limitações reais:

- o bean precisa ser de escopo **normal**; `@Singleton` e `@Dependent` **não** são mockáveis
  (precisam de proxy);
- qualquer objeto pode ser mockado, não só beans CDI.

### 6.3 Dependência — atenção à versão

O livro usa `quarkus-junit5-mockito`. **No Quarkus 3.39.3 esse artefato é um stub de
relocation** (verificado no `~/.m2`): aponta para `quarkus-junit-mockito`, conforme o
migration guide 3.31.

```xml
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-junit-mockito</artifactId>
    <scope>test</scope>
</dependency>
```

> Nota do próprio repo (`reservation-service/pom.xml`) registra o mesmo. Quando seguir o
> livro, **confira o artefato na versão fixada**.

## 7. Perfis de teste (cap. 5.4)

Motivo declarado no livro: (a) isolar estado entre grupos de teste; (b) **testar
configurações diferentes**, o que é impossível quando toda a suíte divide uma instância.

`QuarkusTestProfile` pode declarar:

- overrides de configuração (`getConfigOverrides()`);
- perfil base de configuração;
- beans `@Alternative` habilitados;
- `QuarkusTestResourceLifecycleManager` a subir;
- **tags**, para filtrar a execução;
- parâmetros de linha de comando.

🧪 **No repositório**, `StagingTest` prova que o override funciona de fato — injeta a
propriedade e confere o valor:

```java
@QuarkusTest
@TestProfile(RunWithStaging.class)
class StagingTest {
    @ConfigProperty(name = "quarkus.smallrye-graphql-client.inventory.url")
    String inventoryUrl;

    @Test
    void testStagingProfileOverridesGraphQLUrl() {
        assertEquals("http://staging.service.com/graphql", inventoryUrl);
    }
}
```

Fonte: `reservation-service/src/test/java/org/acme/reservation/StagingTest.java`

Filtro por tag:

```bash
./mvnw test -Dquarkus.test.profile.tags=staging
```

⚠️ **Armadilha:** perfis de teste **aumentam** o tempo da suíte, porque cada
perfil derruba a instância e sobe outra. Não use perfil para "ajustar um valor" — use para
"rodar a mesma aplicação em outro ambiente".

## 8. Mapa: capítulo 5 → o que existe no projeto

| Conceito do livro | Existe aqui? | Onde |
|---|---|---|
| `@QuarkusTest` | ✅ | todos os testes de adapter |
| Injeção de bean no teste | ✅ | `BillingFlowKafkaIntegrationTest` (o `VehicleRegisteredEventPublisherTest` é JUnit puro, monta o adapter à mão) |
| RestAssured | ✅ | `ReservationResourceTest`, `ReactiveExecutionResourceTest` |
| `@TestHTTPEndpoint` | ⚠️ | não usamos — e nem `@TestHTTPResource`: a estratégia é **porta 0** (`.mvn/maven.config` + `%test`) |
| `@Mock` (substituição de bean) | 🚧 | não adotado como padrão |
| `QuarkusMock` + Mockito | 🚧 | não é o padrão; ver [04](./04-estrategia-de-testes-do-projeto.md) |
| `@TestProfile` | ✅ | `StagingTest` |
| `QuarkusTestResourceLifecycleManager` | ✅ | `BillingKafkaCompanionResource` (ver [11](./11-armadilhas-e-licoes.md)) |
| `@QuarkusIntegrationTest` | ⚠️ | `ReservationResourceIT` (só JVM); native ainda 🔜 — [roadmap](../roadmap.md) `Part 3` |

## 9. Checklist de estudo

- [ ] Sei dizer quais instalações do framework não funcionam em nativo e por quê.
- [ ] Sei explicar a diferença entre `@Mock` de bean e `QuarkusMock`.
- [ ] Sei dizer por que escopo `@Singleton`/`@Dependent` não é mockável.
- [ ] Sei escrever um `QuarkusTestProfile` só com override.
- [ ] Sei explicar o custo de usar perfis de teste.
- [ ] Sei identificar o artefato correto de Mockito no Quarkus 3.39.3.

**Veja também:** [04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md) ·
[12-modelo-para-novos-capitulos.md](./12-modelo-para-novos-capitulos.md)

---

_Última atualização: 2026-09-26 (cap. 5; APIs conferidas contra quarkus-junit 3.39.3 e
smallrye-reactive-messaging-api 4.37.0)._
