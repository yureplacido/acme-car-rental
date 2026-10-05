# Knowledge Base — ACME Car Rental

> **Finalidade:** material de **estudo** derivado dos capítulos já implementados do
> *Quarkus in Action* (Štefanko & Martiška, Manning, 2025), cruzado com o código deste
> repositório e com o padrão obrigatório de [ddd-tdd-standards.md](../ddd-tdd-standards.md).
>
> Estas páginas **não substituem** [testing.md](../testing.md), [architecture.md](../architecture.md)
> ou [services.md](../services.md). Aqui registramos **o conceito, a decisão e a lição**;
> lá ficam os fatos operacionais do sistema.

## Por que esta pasta existe

O livro ensina Quarkus. O repositório ensina Quarkus **com DDD, TDD e arquitetura hexagonal**.
Isso cria um learn-by-doings em que muita coisa se perde no caminho:

- o capítulo ensina `@Mock`, mas o projeto **não** usa mock de bean em teste de domínio;
- o capítulo ensina `await().indefinitely()`, mas o padrão do projeto **proíbe** isso em
  caminho que deveria permanecer não bloqueante;
- o capítulo ensina a gravar no banco e *depois* publicar no broker, e o projeto registra
  que isso cria uma janela de inconsistência (ADR 004/008).

Estas páginas existem para tornar essas divergências **explícitas e reutilizáveis**.

## Como este material é escrito

| Convenção | Significado |
|---|---|
| 📘 **Livro** | O que o capítulo ensina, em síntese |
| 🧪 **No repositório** | O que realmente está em código, com caminho do arquivo |
| 🔀 **Divergência** | Onde nos afastamos do livro, e por quê |
| ⚠️ **Armadilha** | Erro real já cometido neste projeto e como foi diagnosticado |
| 🧩 **Exemplo didático** | Código ** propositalmente fora do padrão**, só para estudo |

> Todo bloco marcado 🧩 existe **apenas** para comparação. Nenhum código assim deve
> chegar a `src/main`. Ver [10-exemplos-contrarios-ao-dominio.md](./10-exemplos-contrarios-ao-dominio.md).

## Índice

| # | Documento | Capítulos | Foco |
|---|---|---|---|
| 01 | [Fundamentos, dev mode e configuração](./01-fundamentos-e-dev-mode.md) | 1, 2, 3 | Extension model, live reload, profiles, Dev Services, continuous testing |
| 02 | [Comunicação entre serviços](./02-comunicacoes.md) | 4 | REST, REST client, GraphQL, gRPC, contratos e versionamento |
| 03 | [Testes Quarkus (o framework do livro)](./03-testes-quarkus.md) | 5 | `@QuarkusTest`, RestAssured, mocking, test profiles, native/`IT` |
| 04 | [Estratégia de testes deste projeto](./04-estrategia-de-testes-do-projeto.md) | 5 + padrão | **as 5 camadas, nomenclatura, evidência TDD, testes reativos** |
| 05 | [Web, segurança OIDC e modo produção](./05-web-oauth-e-modo-producao.md) | 6 | Qute/HTMX, Authorization Code, Keycloak, `quarkus:prod` |
| 06 | [Persistência, transações e dados reativos](./06-persistencia-transacoes-e-nosql.md) | 7 | Panache active record/repository, JPA, REST Data, `@WithTransaction`, NoSQL |
| 07 | [Programação reativa](./07-programacao-reativa.md) | 8 | event loop × worker, Mutiny, backpressure, timeout, cancelamento, Loom |
| 08 | [Messaging reativo](./08-messaging-reativo.md) | 9 | canais, `@Incoming`/`@Outgoing`, acks, `Emitter`, connectors Kafka/RabbitMQ |
| 09 | [Padrões de resiliência em messaging](./09-padroes-de-resiliencia-em-messaging.md) | 9 + ADRs | idempotência, retry, DLQ, inbox transacional, outbox transacional |
| 10 | [Exemplos contrários ao domínio](./10-exemplos-contrarios-ao-dominio.md) | todos | **estudo comparativo**: o código "errado" e por quê |
| 11 | [Armadilhas e lições](./11-armadilhas-e-licoes.md) | todos | erros reais, diagnóstico e prevenção |
| 12 | [Modelo para novos capítulos](./12-modelo-para-novos-capitulos.md) | — | **template** para documentar o próximo capítulo |
| 13 | [Transactional Outbox na prática](./13-transactional-outbox.md) | 9 + implementação | **outbox, relay, at-least-once, scheduler e testes E2E** |
| 14 | [Cloud-native patterns](./14-cloud-native-patterns.md) | 10 | **health, metrics Prometheus/Micrometer, Kafka pipeline metrics, tracing, fault tolerance; service discovery 🔜** |
| — | [Índice do livro](./book-index/README.md) | 1–12 | **onde o assunto está**, com página. Não contém o texto da obra |

## Mapa capítulo → documento

| Cap. | Tema do livro | Documento de estudo | Onde o fato operacional mora |
|---|---|---|---|
| 1 | O que é Quarkus | [01](./01-fundamentos-e-dev-mode.md) | — (conceitual) |
| 2 | Primeira aplicação | [01](./01-fundamentos-e-dev-mode.md) | [services.md](../services.md) |
| 3 | Dev mode, config, Dev Services, continuous testing | [01](./01-fundamentos-e-dev-mode.md) | [testing.md](../testing.md) |
| 4 | REST / GraphQL / gRPC | [02](./02-comunicacoes.md) | [architecture.md](../architecture.md), [contracts.md](../contracts.md) |
| 5 | Testing | [03](./03-testes-quarkus.md) + [04](./04-estrategia-de-testes-do-projeto.md) | [testing.md](../testing.md) |
| 6 | Web apps + segurança | [05](./05-web-oauth-e-modo-producao.md) | [deployment.md](../deployment.md) |
| 7 | Database access | [06](./06-persistencia-transacoes-e-nosql.md) | [services.md](../services.md) |
| 8 | Reactive programming | [07](./07-programacao-reativa.md) | [architecture.md](../architecture.md) |
| 9 | Quarkus messaging | [08](./08-messaging-reativo.md) + [09](./09-padroes-de-resiliencia-em-messaging.md) | [adr/](../adr/README.md), [contracts.md](../contracts.md) |
| 10+ | Cloud-native, observabilidade, extensões | [14](./14-cloud-native-patterns.md) (health/metrics/tracing/FT prontos; descoberta e graceful shutdown em andamento) | [roadmap.md](../roadmap.md) |
| 10 | Health, metrics, tracing, FT, service discovery | [book-index/cap10.txt](./book-index/cap10.txt) (onde o livro trata) | [roadmap.md](../roadmap.md) |
| 11 | Cloud, Kubernetes/OpenShift, serverless | [book-index/cap11.txt](./book-index/cap11.txt) | [deployment.md](../deployment.md) |
| 12 | Custom extensions | [book-index/cap12.txt](./book-index/cap12.txt) | [roadmap.md](../roadmap.md) |
| Outbox | Transactional Outbox + relay | [13](./13-transactional-outbox.md) | [adr/](../adr/README.md), [contracts.md](../contracts.md) |

## Versão de referência

Tudo aqui foi escrito contra:

- **Java 21**
- **Quarkus 3.39.3** (fixado em `quarkus.platform.version`)

> ⚠️ O **livro** é de outra versão: *Quarkus in Action* usa **Quarkus 3.15.1 /
> MicroProfile 6.1**. Nenhum item de roadmap é uma transcrição de capítulo —
> ver [roadmap.md](../roadmap.md) § "Como ler este roadmap".

Toda API ou propriedade **sensível à versão** foi conferida no artefato real do
`~/.m2` ou na documentação oficial antes de ser afirmada aqui. Exemplos já
verificados: `quarkus-junit-mockito`, `Acknowledgment.Strategy`,
`quarkus.test.continuous-testing`, `io.quarkus.test.kafka.KafkaCompanionResource`.

## Onde o livro trata cada assunto

[book-index/](./book-index/README.md) é o sumário navegável do livro, por
capítulo, com número de página. Serve para responder *"onde eu leio sobre X?"*
antes de implementar.

O **texto integral não está no repositório** — é obra comercial. Ele é gerado
localmente em `book-text/` (ignorado pelo git) por
`tools/book-index/extract.py`; o que se versiona é só o índice. A regra é a
mesma de [12](./12-modelo-para-novos-capitulos.md): **síntese, não transcrição**.

## Como manter

1. Ao terminar um capítulo do livro, preencha [12-modelo-para-novos-capitulos.md](./12-modelo-para-novos-capitulos.md).
2. Toda divergência do livro precisa de **três** coisas: o que o livro faz, o que fazemos, e a ADR/regra que justifica.
3. Toda armadilha vira entrada em [11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md) assim que for diagnosticada — não quando for resolvida.
4. Nenhum exemplo de código entra aqui sem indicar se é **real** ou **didático**.
5. Se o código e o documento divergirem, o código vence — corrija o documento no mesmo commit.
6. Para refazer o índice do livro: `tools/book-index/extract.py <pdf>` e commitar
   **só** `book-index/`. Nunca versionar `book-text/`.

---

_Última atualização: 2026-10-05 (cap. 10 documentado em [14](./14-cloud-native-patterns.md):
health, métricas do pipeline Kafka/outbox, tracing ponta a ponta e fault tolerance nas chamadas
externas ([adr/009](../adr/009-fault-tolerance-chamadas-externas.md)); cap. 11–12 seguem em
nível de capacidade)._
