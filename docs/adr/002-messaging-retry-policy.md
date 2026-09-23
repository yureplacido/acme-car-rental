# ADR 002 — Política de retry para consumo de mensagens

- **Status:** Accepted
- **Data:** 2026-09-23
- **Contexto:** Capítulo 9 — Quarkus Messaging
- **Escopo:** consumidores de mensagens dos serviços de negócio

## Contexto

O consumo de eventos Kafka pode falhar por motivos transitórios, como indisponibilidade temporária de dependências externas, falhas de rede ou contenção de recursos.

O projeto já possui uma camada transversal de idempotência que faz o claim do `eventId` antes do processamento e libera o claim quando o processamento termina em `nack`.

O retry precisa respeitar essa semântica:

```text
Kafka
  ↓
Reactive Messaging
  ↓
IdempotencyMiddleware
  ↓
Consumer / Use Case
  ↓
falha
  ↓
NACK
  ↓
retry
  ↓
nova tentativa
```

Retry não deve ser implementado dentro de cada consumer nem dentro do middleware de idempotência.

## Decisão

A política de retry será tratada como uma preocupação de **messaging/infrastructure**, separada da lógica de negócio e da idempotência.

O `IdempotencyMessagingDecorator` continuará responsável somente por:

1. identificar a ocorrência pelo `eventId`;
2. obter o claim antes do processamento;
3. descartar duplicatas já processadas;
4. liberar o claim quando a mensagem for `nack`.

O mecanismo de retry será configurado no pipeline de Reactive Messaging/Kafka.

A responsabilidade fica separada desta forma:

```text
                 ┌──────────────────────────┐
                 │ Idempotency Middleware   │
                 │ claim / duplicate /      │
                 │ release on NACK          │
                 └────────────┬─────────────┘
                              ↓
                         Consumer
                              ↓
                         Use Case
                              ↓
                         success → ACK
                              │
                              └── failure → NACK
                                           ↓
                                      Retry Policy
```

## Semântica do retry

Uma falha de processamento produz `nack`.

Antes que o retry seja realizado, o claim associado ao `eventId` deve estar liberado. Isso permite que a nova entrega da mesma ocorrência seja aceita pelo middleware.

Assim:

```text
attempt 1
   ↓
claim(eventId) = true
   ↓
processamento falha
   ↓
NACK
   ↓
release(eventId)
   ↓
retry / redelivery
   ↓
claim(eventId) = true
   ↓
processamento novamente
```

Se o processamento terminar com sucesso:

```text
claim(eventId) = true
   ↓
processamento
   ↓
ACK
   ↓
claim permanece registrado
```

## Limites

Retry deve possuir limites explícitos.

A política deve definir:

- número máximo de tentativas;
- intervalo entre tentativas;
- estratégia de backoff;
- comportamento após o esgotamento das tentativas;
- observabilidade das tentativas e das falhas.

O sistema não deve permitir retry infinito ou um ciclo de redelivery sem limite operacional.

O comportamento após o esgotamento das tentativas será tratado pela política de **Dead Letter Queue**, registrada em ADR próprio.

## Separação de responsabilidades

### Idempotência

Responde à pergunta:

> "Esta ocorrência de evento já foi aceita para processamento?"

### Retry

Responde à pergunta:

> "Uma falha de processamento deve resultar em nova tentativa?"

### DLQ

Responde à pergunta:

> "O que fazemos quando a mensagem continua falhando depois das tentativas permitidas?"

Essas políticas são relacionadas, mas não devem ser implementadas como uma única responsabilidade.

## Falhas permanentes e transitórias

O retry é destinado principalmente a falhas transitórias.

A classificação entre falhas transitórias e permanentes deve permanecer próxima da infraestrutura que conhece a política de entrega, sem introduzir regras de transporte no domínio.

Uma futura política mais sofisticada poderá evitar retry para determinadas classes de erro, desde que isso seja demonstrado por testes e documentado como uma decisão arquitetural.

## Consequências

### Positivas

- consumers permanecem focados no caso de uso;
- retry não fica duplicado entre bounded contexts;
- idempotência e retry possuem responsabilidades claramente separadas;
- o mecanismo permite evoluir posteriormente para DLQ;
- o comportamento pode ser validado por testes de infraestrutura.

### Negativas

- retry passa a depender da configuração do mecanismo de messaging adotado;
- backoff e limites precisam ser tratados como configuração operacional;
- uma falha transitória pode aumentar a latência de processamento;
- a observabilidade precisa distinguir tentativa inicial de redelivery.

## Fora do escopo

Esta ADR não define:

- persistência durável da Inbox;
- implementação da DLQ;
- Outbox;
- exactly-once de negócio;
- retry de chamadas HTTP ou banco de dados dentro do caso de uso;
- classificação definitiva de todas as exceções do domínio.

Esses assuntos possuem decisões próprias.

## Implementação nesta etapa

O consumidor Kafka utiliza a estratégia `delayed-retry-topic` do conector SmallRye Reactive Messaging. As tentativas são encaminhadas para tópicos de retry com atrasos progressivos de 1s, 5s e 15s, com no máximo 3 retries e timeout global de 30s por ocorrência.

Configuração aplicada em `billing-service/src/main/resources/application.properties`:

```properties
mp.messaging.incoming.vehicle-registered-in.failure-strategy=delayed-retry-topic
mp.messaging.incoming.vehicle-registered-in.delayed-retry-topic.topics=vehicle-registered-retry_1000,vehicle-registered-retry_5000,vehicle-registered-retry_15000
mp.messaging.incoming.vehicle-registered-in.delayed-retry-topic.max-retries=3
mp.messaging.incoming.vehicle-registered-in.delayed-retry-topic.timeout=30000
```

Essa abordagem mantém o retry fora do caso de uso e evita bloquear a partição enquanto uma ocorrência aguarda o próximo intervalo de tentativa. O conector preserva a chave e os metadados do registro ao encaminhá-lo para os tópicos de retry.

> A estratégia de DLQ após o esgotamento das tentativas permanece fora desta etapa e será definida na ADR 003.

## Estratégias de falha disponíveis no conector Kafka

O conector Kafka suporta as seguintes estratégias de tratamento de falhas no consumo:

| Estratégia | Descrição |
|---|---|
| `fail` | Propaga a falha e interrompe o processamento do conector. É a estratégia padrão. |
| `ignore` | Ignora a falha após registrá-la e continua o consumo. |
| `dead-letter-queue` | Publica a mensagem que falhou em um tópico de Dead Letter Queue. |
| `delayed-retry-topic` | Encaminha mensagens que sofreram `nack` para tópicos de retry com atraso, permitindo novas tentativas sem bloquear o consumidor original. |

Nesta ADR, a estratégia adotada é `delayed-retry-topic`.

## Tópicos de retry

Os tópicos são recursos Kafka reais e seus nomes são configurados explicitamente em `delayed-retry-topic.topics`.

| Tópico | Atraso | Papel |
|---|---:|---|
| `vehicle-registered-retry_1000` | 1s | Primeiro retry |
| `vehicle-registered-retry_5000` | 5s | Segundo retry |
| `vehicle-registered-retry_15000` | 15s | Terceiro retry |

O sufixo numérico do tópico é interpretado pela estratégia como o atraso em milissegundos. Portanto, neste caso, `1000`, `5000` e `15000` representam 1s, 5s e 15s respectivamente.

A nomenclatura é uma convenção deliberada do projeto para tornar a topologia legível. O tópico precisa ser provisionado no Kafka; a estratégia de retry não deve ser confundida com um mecanismo que cria esses recursos automaticamente.

Com `max-retries=3`, as três faixas de atraso são utilizadas em sequência:

```text
tentativa inicial
      ↓ NACK
retry_1000   → 1s
      ↓ NACK
retry_5000   → 5s
      ↓ NACK
retry_15000  → 15s
      ↓
nova tentativa
```

Se houver menos tópicos configurados do que retries permitidos, a estratégia pode reutilizar o último tópico para as tentativas adicionais.

## Evidência esperada

A implementação desta decisão deverá demonstrar, por testes:

- uma mensagem que falha pode ser entregue novamente;
- o claim de idempotência é liberado antes da nova tentativa;
- uma mensagem que termina com sucesso não é processada novamente;
- o número máximo de tentativas é respeitado;
- após o limite, a mensagem segue para o mecanismo definido de DLQ.

A evidência deve usar o mesmo pipeline de Reactive Messaging utilizado pelo consumidor real, evitando testar apenas uma chamada direta ao método do consumer.

## Relação com ADR 001

Esta decisão complementa **ADR 001 — Idempotência transversal no pipeline de mensagens**.

A responsabilidade permanece:

```text
              mensagem
                  ↓
          idempotency claim
                  ↓
              consumer
                  ↓
             ACK / NACK
                  ↓
             retry policy
                  ↓
                DLQ
```

O retry não substitui idempotência. A redelivery da mesma ocorrência deve continuar sendo identificada pelo `eventId`.
