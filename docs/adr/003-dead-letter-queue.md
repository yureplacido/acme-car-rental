# ADR 003 — Dead Letter Queue para consumo de mensagens

- **Status:** Accepted
- **Data:** 2026-09-23
- **Contexto:** Capítulo 9 — Quarkus Messaging
- **Escopo:** consumidores de mensagens dos serviços de negócio (extensão da ADR 002)

## Contexto

A política de retry (ADR 002) usa `delayed-retry-topic` com `max-retries=3`. Antes desta
ADR, após o esgotamento das tentativas o record era **abandonado** (descartado): o conector
loga `delayedRetryNoDlq` (`SRMSG18280`) e não produz nenhum artefato recuperável.

Isso é suficiente para falhas tratadas, mas cobra um custo operacional para falhas
permanentes:

- não há trilha do que falhou e por que falhou;
- não existe mecanismo para auditoria ou reprocessamento;
- eventos corruptos (ex.: JSON sem `eventId`) desaparecem sem rastro consumível.

## Decisão

Após o esgotamento dos retries, o record deve ser roteado para um **tópico de
dead-letter** em vez de ser abandonado.

A DLQ é uma preocupação de **messaging/infrastructure**, configurada no pipeline de
Reactive Messaging/Kafka, e permanece fora do domínio de negócio.

O conector SmallRye Reactive Messaging Kafka 4.37.0 oferece, por canal, a propriedade:

```properties
mp.messaging.incoming.<channel>.dead-letter-queue.topic=<topic>
```

Em conjunto com `failure-strategy=delayed-retry-topic`, essa propriedade é consultada
quando as tentativas se esgotam: o record que ainda está falhando é publicado no tópico
de dead-letter em vez de ser descartado.

## Semântica

```text
tentativa inicial
      ↓ NACK
retry_1000   → 1s
      ↓ NACK
retry_5000   → 5s
      ↓ NACK
retry_15000  → 15s
      ↓ NACK
esgotou max-retries
      ├─ dead-letter-queue.topic configurado → publica em <dlq-topic>
      └─ sem dead-letter-queue.topic         → abandona (com log SRMSG18280)
```

Características verificadas na implementação do SmallRye 4.37.0 (`KafkaDelayedRetryTopic`):

- **Chave preservada:** o record de dead-letter mantém a chave original do produtor;
  assim, a DLQ pode ser agrupada/auditada pela identidade de negócio (ex.: `eventId`).
- **Payload preservado:** o conteúdo original é mantido intacto.
- **Metadados presentes** via headers:

| Header | Conteúdo |
|---|---|
| `delayed-retry-reason` | motivo do `nack` (mensagem da exceção da última falha) |
| `delayed-retry-count` | contador de tentativas |
| `delayed-retry-topic` | tópico do qual o record veio na última entrega (o último tópico de retry) |
| `delayed-retry-partition/offset` | posição original no tópico de origem |
| `delayed-retry-original-timestamp` / `delayed-retry-first-processing-timestamp` | timestamps de origem e primeira tentativa |
| `delayed-retry-exception-class-name` / `delayed-retry-cause-class-name` / `delayed-retry-cause` | detalhe da exceção |

> Nota: `delayed-retry-topic` documenta a origem **da última entrega** (o último tópico de
> retry antes da DLQ), não o tópico original. Para rastrear a origem real, use os headers de
> offset/timestamp do registro.

A configuração é **por canal**, no mesmo prefixo do canal de consumo. Ela não conflita com
as propriedades `delayed-retry-topic.*` da ADR 002.

## Implementação nesta etapa

Canal de consumo real em `billing-service/src/main/resources/application.properties`:

```properties
mp.messaging.incoming.vehicle-registered-in.failure-strategy=delayed-retry-topic
mp.messaging.incoming.vehicle-registered-in.delayed-retry-topic.topics=vehicle-registered-retry_1000,vehicle-registered-retry_5000,vehicle-registered-retry_15000
mp.messaging.incoming.vehicle-registered-in.delayed-retry-topic.max-retries=3
mp.messaging.incoming.vehicle-registered-in.delayed-retry-topic.timeout=30000
mp.messaging.incoming.vehicle-registered-in.dead-letter-queue.topic=vehicle-registered-dlq
```

O tópico `vehicle-registered-dlq` é provisionado pelo serviço `kafka-init` do
`docker-compose` de `others/` (1 partição, RF 1), junto com os tópicos de fonte e retry.

A política se aplica a qualquer falha do pipeline (transitória que esgota os retries ou
permanente), incluindo eventos corruptos sem `eventId`: o `IdempotencyMessagingDecorator`
repassa (sem claim) o que não consegue extrair `eventId`; a falha ocorre no consumer e
segue o mesmo caminho de retry → DLQ.

## Evidência esperada

Após o esgotamento dos retries:

- o record é publicado no tópico de dead-letter configurado (não abandonado);
- a chave e o payload originais são preservados;
- os headers de diagnóstico (reason, count, topic, exception) estão presentes;
- não há novas tentativas de consumo depois que o record foi para a DLQ.

No stack local (`docker compose`), a chegada na DLQ é observável pelo log do conector
(`SRMSG18278: ... sending the record to topic vehicle-registered-dlq`).

> Em `@QuarkusTest` com `KafkaCompanionResource`, os canais configurados em
> `src/main/resources` não materializam emissor/consumidor Kafka; os canais definidos em
> `src/test/resources` sim. Por isso o teste de integração usa um canal de teste dedicado
> (`dlq-test-in`) com o mesmo pipeline `delayed-retry-topic` + DLQ e um consumer de teste
> que nacka como um evento corrupto. O consumer real é validado por unit test
> (`KafkaVehicleRegisteredConsumerTest`) e o caminho real completo é verificado E2E no stack.

## Consequências

### Positivas

- nenhuma falha permanente desaparece sem rastro;
- cabe ao operador implementar consumo, alerta e reprocessamento da DLQ;
- a DLQ não aumenta a responsabilidade do consumer de negócio nem do middleware;
- o mecanismo é nativo do conector (SmallRye), sem código próprio de transporte.

### Negativas

- a DLQ por si só não reprocessa: exige ferramenta/task de replay;
- tópicos adicionais precisam ser provisionados e monitorados (volume/age);
- mensagens na DLQ podem ficar estagnadas sem alerta;
- ausência de DLQ como artefato de negócio: a disciplina operacional é responsabilidade da equipe.

## Fora do escopo

Esta ADR não define:

- consumer da DLQ / requeue automático;
- schema registry ou validação de schema na DLQ;
- alertas/observabilidade sobre volume e idade de mensagens na DLQ;
- DLQ por tipo de falha (classificação de exceções) — o conector roteia qualquer `nack` esgotado;
- outbox/inbox persistente (consistência DB + eventos).

Esses assuntos possuem decisões próprias.

## Relação com ADR 001 e ADR 002

- **ADR 001** (idempotência): a DLQ recebe inclusive os eventos sem `eventId` (que nem
  chegam a obter claim), preservando-os para auditoria.
- **ADR 002** (retry): a DLQ define o destino do record quando o retry esgota. O limite de
  tentativas continua sendo da ADR 002; esta ADR só muda o *destino* final — de "abandonar"
  para "publicar na DLQ".