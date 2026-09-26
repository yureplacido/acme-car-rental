# 01 — Fundamentos, dev mode e configuração

> Capítulos 1, 2 e 3 do *Quarkus in Action*.
> Fonte da verdade do **fato**: código. Fonte da verdade do **padrão**: [ddd-tdd-standards.md](../ddd-tdd-standards.md).

## 1. O que o livro ensina

### 1.1 A tese do Quarkus (cap. 1)

Quarkus não é "Spring mais rápido". A mudança conceitual é o **modelo de extensão em
tempo de build**: o framework lê a aplicação, o *deployment* de cada extensão é acionado
em build e o que chega ao runtime é código específico — sem reflexão, sem classpath
varrido em runtime, sem class loading reflection-based.

Três consequências práticas que aparecem o tempo todo no projeto:

| Consequência | Onde aparece no repositório |
|---|---|
| Configuração em build é validada cedo | `quarkus.rest-client."FQCN".uri` (reservation) usa a classe como chave; `quarkus.rest-client.<nome>.url` (users) usa o nome do `@RegisterRestClient` |
| Persistência é replanejada em build | `drop-and-create` + `import.sql` são conhecidos antes do boot |
| Erros de configuração aparecem no boot, não no request | `%prod.quarkus.oidc.auth-server-url` com default explícito |

### 1.2 Dev mode (cap. 3.1)

O dev mode não é "rodar com hot reload". Ele faz, entre outras coisas:

- **live reload** de classes e de recursos;
- **reconfiguração** quando `application.properties` muda;
- **continuous testing** integrado (cap. 3.5);
- **Dev UI** e **Dev Services** (cap. 3.3 / 3.4);
- CDI *arc* com beans de teste e mocks disponíveis.

Comandos úteis neste repositório:

```bash
./mvnw -pl inventory-service quarkus:dev
# continuous testing: p = pause tests, r = resume/re-run, o = toggle test output
# dev mode:          s = force restart, l = live reload, q = quit, h = help
```

Comandos confirmados no fonte de 3.39.3 (`TestConsoleHandler`): `r` "Resume testing" /
"Re-run all tests", `o` "Toggle test output", `p` "Pause tests". As duas mais úteis para
continuous testing são justamente `p` e `r` — parar enquanto se escreve o teste, e rodar
tudo quando o teste compila.

### 1.3 Configuração e perfis (cap. 3.2)

O Quarkus SmallRye Config resolve por **ordem de precedência** e aplica **perfis**:

```text
application.properties
  ↓ overrides de src/main/resources por profile (%prod, %docker, %test)
  ↓ variáveis de ambiente
  ↓ system properties
  ↓ -Dquarkus.profile=common,dev
```

Perfis embutidos: `dev` (dev mode), `test` (rodando testes), `prod` (default fora de dev/test).
`%docker` é um perfil **criado pelo projeto**, não pelo Quarkus.

🧪 **No repositório** — a mesma chave, três ambientes:

```properties
# inventory-service/src/main/resources/application.properties
kafka.bootstrap.servers=localhost:9092
%docker.kafka.bootstrap.servers=kafka:9092
%prod.kafka.bootstrap.servers=localhost:9092
```

### 1.4 Dev Services (cap. 3.4)

Dev Services sobe dependências reais em container **sem configuração**. A regra prática:

> Se a extensão que você usa tem Dev Service, **não escreva a URL** em dev/test.
> Escreva a URL **só** em `%prod` e `%docker`.

🧪 **No repositório**, isso aparece como ausência deliberada de propriedade:

| Serviço | Dependência | Dev/test | `%prod` / `%docker` |
|---|---|---|---|
| `inventory-service` | MySQL | sem URL → Dev Service | `mysql://...:3306/inventory` |
| `reservation-service` | PostgreSQL | sem URL → Dev Service | `vertx-reactive:postgresql://...` |
| `rental-service` | MongoDB | sem connection-string → Dev Service | `mongodb://...:27017` |

⚠️ **Kafka NÃO tem Dev Service no Quarkus.** Verificado no 3.39.3: existem
`quarkus-devservices-postgresql`, `-mysql`, `-mariadb`, `-mongodb` (dentro da extensão
Mongo), `-keycloak` e `-oidc`, mas **não existe `quarkus-devservices-kafka`**. O Strimzi é usado por Testcontainers, não pelo
mecanismo de Dev Services. Por isso `inventory-service` e `billing-service` **declaram
explicitamente**:

```properties
kafka.bootstrap.servers=localhost:9092
%docker.kafka.bootstrap.servers=kafka:9092
%prod.kafka.bootstrap.servers=localhost:9092
```

E o broker de teste vem de outro caminho: `quarkus-test-kafka-companion` +
`BillingKafkaCompanionResource` — ver [08-messaging-reativo.md](./08-messaging-reativo.md).
Ou seja: a regra acima ("sem URL → sobe sozinho") vale **para o que tem Dev Service**, e o
Kafka é justamente o contraexemplo do repositório.

🔀 **Divergência:** o livro apresenta Dev Services como recurso de produtividade. Aqui ele
vira também **estratégia de teste**, porque é o que permite `@QuarkusTest` com banco real
sem código de setup.

### 1.5 Continuous testing (cap. 3.5)

Cada reload no dev mode dispara a suíte. Ponto importante e pouco citado: os testes rodam
em um **class loader separado**, então rodam em paralelo com a aplicação em dev sem
contaminar o estado dela.

```properties
# liga os testes já no startup do dev mode
quarkus.test.continuous-testing=enabled
```

Sem essa propriedade o comportamento padrão é *habilitado mas pausado*
(`Tests paused, press [r] to resume`).

⚠️ **Armadilha real do projeto:** continuous testing **não** cobre teste nativo e
compartilha a máquina com containers. Com Postgres/Mongo de Dev Services **e** o broker
Kafka do Kafka Companion (Testcontainers), a suíte completa de reativos e messaging fica
pesada para o ciclo de reload. Ver
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md).

## 2. O que o projeto acrescenta

### 2.1 Porta de teste não colide com porta de dev

O livro recomenda `quarkus.http.test-port`. O projeto **zera** a porta em `%test`:

```properties
# billing-service, reservation-service
%test.quarkus.http.test-port=0
```

Porta `0` = porta livre escolhida pelo SO. Benefício duplo: não briga com o dev mode e
duas execuções simultâneas (CI, testes em paralelo) não colidem.

Além do `%test` por serviço, o **agregador** já força isso para todos os módulos em
`.mvn/maven.config`:

```properties
-Dquarkus.http.test-port=0
```

Ou seja, o `quarkus.http.test-port=0` é garantido mesmo em serviço que não declara o
perfil — é assim que `docs/architecture.md` documenta a regra.

### 2.2 Perfis nomeados por ambiente de deploy, não por framework

`%dev`, `%test` e `%prod` são do Quarkus. `%docker` é nosso: representa "rodando dentro do
`others/docker-compose.yml`", onde os hosts são nomes de serviço, não `localhost`.

### 2.3 Configuração é documento operacional

Cada `application.properties` carrega comentário com o capítulo do livro que justifica a
propriedade. Isso cria rastreabilidade capítulo → configuração:

```properties
# Cap.8 - Hibernate Reactive + Panache + MySQL reativo.
# Cap.9 - Kafka via SmallRye Reactive Messaging.
# Cap.7.6 - MongoDB via Panache (active record).
```

## 3. Checklist de estudo

- [ ] Sei explicar por que `%test` existe como perfil separado de `%dev`.
- [ ] Sei dizer quando **não** escrever URL de datasource (resposta: quando há Dev Service — e lembrar que **Kafka não tem**).
- [ ] Sei explicar o que `quarkus.http.test-port=0` resolve.
- [ ] Sei listar as teclas de continuous testing (`p`, `r`, `o`) e de dev mode (`s`, `l`, `q`, `h`).
- [ ] Sei dizer quais perfis deste repositório são do Quarkus e qual é nosso.

## 4. Referências de código

| Assunto | Arquivo |
|---|---|
| Perfis e datasources | `inventory-service/src/main/resources/application.properties` |
| Perfil `%docker` + OIDC | `users-service/src/main/resources/application.properties` |
| Portas de teste | `billing-service/src/main/resources/application.properties` (`%test.quarkus.http.test-port=0`) |
| Versão fixada | `billing-service/pom.xml` (`quarkus.platform.version=3.39.3`) |

**Veja também:** [02-comunicacoes.md](./02-comunicacoes.md) ·
[04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) ·
[05-web-oauth-e-modo-producao.md](./05-web-oauth-e-modo-producao.md)

---

_Última atualização: 2026-09-26 (cap. 1–3)._
