# Roadmap — Quarkus + DDD + TDD

> Cada item é uma evidência executável no repositório, não apenas um tópico lido.

## Como ler este roadmap

O livro é escrito contra **Quarkus 3.15.1 / MicroProfile 6.1**. Este repositório
está em **Quarkus 3.39.3** (`quarkus.platform.version`). Por isso:

- **item de roadmap = capacidade + evidência**, não o passo do livro;
- a API concreta (`artifactId`, propriedade, comportamento) é decidida na
  implementação e verificada contra 3.39.3 — regra 16 do [AGENTS.md](../AGENTS.md);
- `[3.39.3]` num item marca exatamente isso: o que o livro usou pode ter mudado;
- o mapa de onde o livro trata cada assunto está em
  [knowledge/book-index/](knowledge/book-index/README.md) — o sumário navegável,
  com página. O texto do livro em si não está no repositório.

## Foundation

- [x] Padronizar módulos independentes sem reactor
- [x] Agregador de testes na raiz (conveniência; sem parent/acoplamento) — `./mvnw test`
- [x] Definir Bounded Context Map
- [x] Definir padrão Domain/Application/Ports/Adapters
- [x] Definir padrão TDD
- [x] Criar AGENTS.md como regra de engenharia
- [x] Criar agentes OpenCode de arquitetura, DDD, TDD e Quarkus
- [x] Criar agente de implementação orientado pelos padrões
- [x] Criar **Domain Designer** para definir o modelo antes da implementação
- [x] Definir Inventory como Fleet/Vehicle bounded context
- [x] Enriquecer Inventory com Vehicle telemetry/condition
- [x] Criar MaintenanceOrder como segundo aggregate do Inventory
- [x] Migrar todos os business services para o DDD baseline
- [x] Estruturar Users como BFF e CLI/Proto como módulos especiais

## Part 1 — Getting started

- [x] Cap. 1-2 — Quarkus e primeira aplicação
- [x] Cap. 3 — Dev mode e continuous testing

## Part 2 — Developing applications

- [x] Cap. 4 — REST, GraphQL e gRPC
- [x] Cap. 5 — Testing
- [x] Cap. 6 — Web, OIDC e segurança
- [x] Cap. 7 — Database access
- [ ] Cap. 8 — Reactive programming
- [ ] Cap. 9 — Quarkus Messaging

## Cap. 8 — Reactive programming

- [x] Reservation usa Hibernate Reactive + PostgreSQL reativo
- [x] Reservation usa GraphQL client tipado reativo
- [ ] Tornar o fluxo de Inventory explicitamente reativo quando a natureza do caso justificar
- [ ] Demonstrar Uni versus Multi em casos de negócio reais
- [ ] Demonstrar event loop versus worker pool com teste/observabilidade
- [ ] Demonstrar concorrência controlada
- [ ] Demonstrar backpressure em um fluxo de ingestão
- [ ] Definir timeout/cancellation/retry nos adapters externos

## Cap. 9 — Messaging

> Status: pipeline Kafka Inventory → Billing (`vehicle-registered`) executável em código, testes e
> stack docker-compose (Kafka provisionado via `kafka-init`), com retry (ADR 002) e DLQ (ADR 003);
> o fluxo de cobrança (Reservation/Rental → Invoice DRAFT→OPEN) é executável em código e testes
> (Kafka → Postgres via Dev Services), com inbox durável (ADR 005).

- [x] Billing recebe eventos de Reservation/Rental
- [x] Definir contratos de eventos e versionamento
- [x] Idempotência de consumidores
- [x] Retry (delayed-retry-topic, ADR 002)
- [x] Kafka provisionado no stack docker (broker KRaft + tópicos via `kafka-init`)
- [x] Dead-letter strategy (ADR 003)
- [x] Outbox/inbox quando o domínio exigir consistência entre DB e eventos

Notas de escopo:

- Billing hoje consome além de `vehicle-registered` (scaffold de aprendizagem; o handler só loga):
  os eventos `ReservationConfirmed`/`RentalCompleted` do fluxo de cobrança — DRAFT→OPEN — em
  `KafkaReservationConfirmedConsumer`/`KafkaRentalCompletedConsumer`, validado por
  `BillingFlowKafkaIntegrationTest` (Kafka real + Postgres). O teste usa `UniAsserter` (sem transação
  envolvente) para que cada leitura veja o efeito commitado pelo consumer; com
  `TransactionalUniAsserter` o cache de primeira camada enxergava sempre o DRAFT e mascarava o UPDATE.
- Idempotência é aplicada no processamento inbound pelo `TransactionalInboxProcessor`: claim e efeito de negócio
  compartilham a mesma transação reativa. O `ProcessedEventStore` permanece atrás de uma porta.
- `ProcessedEventStore.tryClaim(UUID)` representa o claim atômico. A implementação persistente em
  Postgres (`INSERT ... ON CONFLICT DO NOTHING`) cobre o inbox durável (ADR 005); em memória fica
  apenas para os testes de unidade/application.
- Retry é configurado na infraestrutura Kafka (`delayed-retry-topic`, `max-retries=3`, atrasos 1s/5s/15s);
  decisão em `docs/adr/002-messaging-retry-policy.md`. Após o esgotamento, o record vai para a DLQ
  `vehicle-registered-dlq` (decisão em `docs/adr/003-dead-letter-queue.md`). Eventos corruptos também
  percorrem a política: o consumer (que desserializa inline, sem decorator) falha ao não conseguir extrair
  `eventId`; a falha segue retry até a DLQ. Evidência atual: `DlqKafkaIntegrationTest` prova a **estratégia**
  do SmallRye no canal sintético `dlq-test-in` (o `DlqTestConsumer` sempre nacka, sem passar pelo consumer
  real), e `shouldConfigureDeadLetterTopicOnRealVehicleRegisteredChannel` prova a **configuração** do canal
  real. **Dívida:** falta um teste no canal real `vehicle-registered-in` que produza um payload inválido e
  comprove retry → `vehicle-registered-dlq` de ponta a ponta.
- **Broker de dev = Kafka Dev Service do Quarkus 3.39.3** (`%dev.quarkus.kafka.devservices.port=39092`,
  `shared=true` entre os serviços, tópicos via `topic-partitions`); **broker de container/prod =
  `others/docker-compose.yml`** (serviços `kafka` e `kafka-init`, `apache/kafka:3.9.1`), que anuncia
  **dois** listeners — `kafka:29092` (INTERNAL, containers, `%docker.kafka.bootstrap.servers`) e
  `localhost:9092` (EXTERNAL, jar no host, `%prod.kafka.bootstrap.servers`). O `kafka-init` é a fonte
  única dos tópicos no compose, inclusive os de `reservation-confirmed`, `rental-completed` e
  `invoice-opened` do cap.10, e usa `--bootstrap-server kafka:29092`. Nos testes quem fornece o
  broker é o `KafkaCompanionResource` (`%test.quarkus.kafka.devservices.enabled=false`). Ferramentas
  de operação: `docker exec acme-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 ...`
  (compose) ou o container do Dev Service em `39092`.
  **Dívida de operação:** misturar os dois brokers (app no host + container) não compartilha evento —
  para fluxo monolítico de mensageria use o compose nos dois lados ou o Dev Service nos dois.
  A imagem é **`3.9.1` e não `3.9.0`** por causa do bug **KAFKA-18281**: com KRaft 3.9.0 o broker
  validava listeners não-advertised (ex.: `CONTROLLER`) contra `advertised.listeners` e o `0.0.0.0`
  causava falha de inicialização/healthcheck com a nossa configuração — corrigido em 3.9.1.
  Detalhes do mapa cliente→porta e do fluxo local (incluindo o `clean` obrigatório e o
  pré-requisito `inventory-proto install`) em [deployment.md](deployment.md#kafka-cap9);
  armadilhas em [knowledge/11 §15](knowledge/11-armadilhas-e-licoes.md) e
  [§16](knowledge/11-armadilhas-e-licoes.md).
- **Portas de dev previsíveis**: todos os Dev Services têm porta fixa **apenas no `%dev`**
  (MySQL `33306`, Postgres `55432`/`55433`, Mongo `37017`, Keycloak `38180`/`38181`, Kafka `39092`),
  para DBeaver/CLI não dependerem de porta sorteada. No `%test` a porta continua aleatória, porque
  dois builds simultâneos não podem disputá-la. Tabela, regra de formação dos números e credenciais
  em [deployment.md → Portas de desenvolvimento](deployment.md#portas-de-desenvolvimento-dev-services-com-porta-fixa).
- **Dívida (achada em 2026-09-27, cap.9):** o caminho de publicação de evento do
  `inventory-service` está quebrado no `HEAD` por dois bugs anteriores a este trabalho e que
  nenhum teste cobria (o publisher é testado com emitter mockado, sem broker):
  1. `VehicleEntity.condition` é gerado sem escape (`condition` é palavra reservada do MySQL):
     a mutation `register` falha com `SQLGrammarException` (errorCode 1064) — presente desde
     o commit `7d8a954`.
  2. `KafkaEventPublisher` (commit `d34f9b2`, "Feat/kafka partition key") envia
     `KafkaRecord<String,String>` num canal que declara `key.serializer`/`value.serializer`
     explícitos: o SmallRye tenta serializar o wrapper do record e o send falha com
     `SerializationException: Can't convert value of class OutgoingKafkaRecord`. O `billing-service`
     envia `Record<String,String>` no mesmo padrão de config e funciona.
  Com ambos corrigidos em um worktree descartável, o publish E2E foi provado: mutation
  `register` → `vehicle-registered` com chave de partição `vehicleId` no broker do compose.
  **Falta:** corrigir na árvore principal com TDD (teste de integração do publisher contra
  broker real) antes de reaproveitar o cap.9.
- A decisão arquitetural da fronteira transacional está registrada em `docs/adr/007-transactional-inbox.md`.
- Contrato documentado em `docs/contracts.md` (seção `VehicleRegistered (Kafka)`).

## Part 3 — Cloud and beyond

- [ ] Cap. 10 — Health, metrics, tracing, fault tolerance e service discovery
- [ ] Cap. 11 — Imagem de container, Kubernetes/OpenShift e deploy real
- [ ] Cap. 12 — Custom Quarkus extensions
- [ ] Native build e testes de integração em todos os módulos

## Cap. 10 — Cloud-native patterns

> O livro tem **seis** pilares neste capítulo (10.1–10.7, p. 273–303):
> MicroProfile/SmallRye, health, metrics, tracing, fault tolerance e service
> discovery. Onde o assunto está: `docs/knowledge/book-index/cap10.txt`.
>
> Hoje não há **nenhuma** dependência de health, metrics, tracing ou fault
> tolerance nos 5 módulos: este capítulo parte do zero.

- [ ] Decidir MicroProfile antes de abstração própria: health, metrics, tracing e
      fault tolerance vêm de SmallRye, não de código do projeto — evidência: ADR
- [ ] Health de aplicação expondo liveness, readiness e startup como grupos
      distintos, com semântica diferente por grupo [3.39.3] — evidência: teste por serviço
- [ ] Health check de dependência (DB, broker) na semântica correta: dependência
      externa caída derruba readiness, não liveness
- [ ] Métricas HTTP e de runtime coletáveis fora do processo [3.39.3] — evidência: teste
- [ ] Métrica de negócio como valor de domínio (gauge), não só contador de infra
- [ ] Métrica do pipeline Kafka e do relay da outbox: lag, falhas, retries
- [ ] Tracing de requisição ponta a ponta, com propagação de contexto através
      do Kafka [3.39.3] — evidência: teste de integração assegurando a propagação
- [ ] Fault tolerance em chamada externa com timeout, retry e fallback explícitos,
      sem retry cego [3.39.3] — evidência: teste do cenário de falha
- [ ] Service discovery desacoplando localização de serviço da configuração
- [ ] Configuração cloud-native: a mesma imagem sobe em qualquer ambiente,
      comportamento stateless entre instâncias
- [ ] Graceful shutdown drenando requisição in-flight e outbox pendente

## Cap. 11 — Quarkus applications in the cloud

> O livro (11.1–11.7, p. 304–351) cobre config externalizada, imagem de container,
> Kubernetes/OpenShift, clients de cluster, serverless e deploy no OpenShift
> Sandbox. Onde o assunto está: `docs/knowledge/book-index/cap11.txt`.

- [ ] Configuração externalizada por ambiente: mesma imagem em dev, staging e
      prod, sem rebuild [3.39.3] — evidência: dois ambientes com a mesma imagem
- [ ] Imagem de container reproduzível para os 5 módulos, com tag imutável
      [3.39.3] — evidência: build local e digest registrado
- [ ] Manifests Kubernetes versionados no repositório, com customização
      declarativa de recursos, probes e config [3.39.3]
- [ ] Probes de liveness/readiness reaproveitando o health do cap. 10, com a
      mesma configuração valendo em JVM e nativo
- [ ] Deploy do Acme em cluster real (OpenShift Sandbox), com no mínimo dois
      serviços e o pipeline Kafka junto — evidência: URL respondendo
- [ ] Registrar o custo de manter o cluster como parte da evidência de teste

Conceitos do capítulo que **não** usamos:

| Conceito | Por que não usamos | Quando reavaliar |
|---|---|---|
| Clients Kubernetes/OpenShift (11.4) | O projeto não gerencia recursos de cluster, só consome | se surgir necessidade de operator |
| Serverless com Funqy/Knative (11.5) | Os casos de uso são contínuos e com estado; operar serverless custa mais do que rende | se aparecer workload event-driven isolado |
| Push para registry próprio (11.2.4) | Depende de credencial de terceiro no ambiente de build | quando existir registry do time |

## Cap. 12 — Custom Quarkus extensions

> O livro (12.1–12.5, p. 352–368) cobre motivação, módulos, processors/recorders/
> build items, Dev Services, Dev UI, modo nativo e testes de extensão. Onde o
> assunto está: `docs/knowledge/book-index/cap12.txt`.

- [ ] 🔜 Decidir qual extensão o projeto realmente precisa — o livro constrói uma
      extensão de estatísticas; só criamos uma se o domínio pedir
- [ ] Extensão com model de configuração próprio, sem contaminar os módulos de domínio
- [ ] Build step, recorder e processor em módulo próprio, com o código de build
      fora do domínio e da aplicação
- [ ] Comportamento correto em dev mode (Dev Services, Dev UI) **e** em nativo
- [ ] Testes da extensão: unitário do recorder e de integração do artefato

## Base de estudo

- [x] `docs/knowledge/` criada para os capítulos já implementados (cap. 1-9, 13 documentos)
- [x] `knowledge/13-transactional-outbox.md`: estudo da outbox transacional do cap. 9
- [x] Documento de padrão de testes do projeto (`knowledge/04-estrategia-de-testes-do-projeto.md`)
- [x] Exemplos didáticos contrários ao domínio (`knowledge/10-exemplos-contrarios-ao-dominio.md`)
- [x] Registro de armadilhas reais (`knowledge/11-armadilhas-e-licoes.md`)
- [x] Template para novos capítulos (`knowledge/12-modelo-para-novos-capitulos.md`)
- [x] Índice navegável do livro por capítulo, com página
      (`knowledge/book-index/`, 20 arquivos, gerado por `tools/book-index/extract.py`)
- [x] Escopo do cap. 10 conferido contra o livro: o capítulo tem **seis** pilares
      (health, metrics, tracing, fault tolerance, service discovery, SmallRye/MP),
      não só health e metrics
- [ ] Preencher com o cap. 10 (cloud-native patterns / health / metrics) quando implementado

## Regra de evolução

```
Domain Design
      ↓
RED
      ↓
GREEN
      ↓
REFACTOR
      ↓
Adapter / Integration evidence
      ↓
Architecture + DDD + TDD + Quarkus guardians
```

Use `/domain-design` antes de implementar uma feature e `/preflight` para o fluxo completo.
