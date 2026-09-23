# ADR 001 — Idempotência transversal no pipeline de mensagens

- **Status:** Accepted
- **Data:** 2026-09-22
- **Contexto:** Capítulo 9 — Quarkus Messaging
- **Escopo:** consumidores de mensagens dos serviços de negócio

## Contexto

Idempotência é uma preocupação transversal do consumo de mensagens.

Implementá-la dentro de cada consumer cria uma regra que precisa ser lembrada manualmente por cada novo consumidor:

```text
Kafka
  ↓
Consumer
  ↓
tryClaim(eventId)
  ↓
Use Case
```

Esse desenho permite que um consumer novo seja criado sem a proteção de idempotência.

O projeto usa Quarkus Messaging / SmallRye Reactive Messaging, que permite decorar o pipeline de subscribers por meio de `SubscriberDecorator`. Essa extensão é específica do SmallRye e integra a infraestrutura ao grafo de Reactive Messaging.

## Decisão

A idempotência de consumo será implementada como **middleware de messaging**, fora dos consumers de negócio.

O fluxo passa a ser:

```text
Broker
  ↓
Reactive Messaging
  ↓
IdempotencyMessagingDecorator
  │
  ├── evento já processado → ACK + descarta
  │
  └── novo evento
        ↓
      Consumer
        ↓
      Use Case
```

O middleware:

1. intercepta mensagens dos canais de entrada conectados ao broker;
2. extrai o `eventId` do envelope JSON padronizado;
3. executa `ProcessedEventStore.tryClaim(eventId)`;
4. descarta duplicatas antes que cheguem ao consumer;
5. preserva a mensagem para o consumer quando o claim é obtido;
6. libera o claim quando o processamento termina em `nack`;
7. mantém o claim quando o processamento termina com `ack`.

O consumer não depende mais diretamente de `ProcessedEventStore`.

### Contrato da mensagem

Eventos de integração consumidos por essa infraestrutura devem possuir:

```json
{
  "eventId": "uuid",
  "version": 1
}
```

O `eventId` identifica a ocorrência do evento e é a chave usada para deduplicação.

## Consequências

### Positivas

- novos consumers não precisam implementar idempotência manualmente;
- a política fica centralizada na infraestrutura de messaging;
- o código de negócio permanece focado no caso de uso;
- a mesma política pode proteger múltiplos bounded contexts;
- retry e DLQ poderão ser adicionados na mesma camada transversal.

### Negativas

- `SubscriberDecorator` é uma extensão específica do SmallRye Reactive Messaging;
- o middleware depende de um contrato comum de `eventId`;
- falhas no middleware podem impedir o consumo da mensagem;
- a implementação atual ainda usa um store em memória.

## Atomicidade e persistência

A abstração `ProcessedEventStore.tryClaim(UUID)` representa um **claim atômico**.

A implementação atual em memória usa uma estrutura concorrente para demonstrar a semântica:

```text
tryClaim(eventId)
  ↓
atomic add
  ├── true  → processa
  └── false → duplicata
```

A persistência durável da Inbox permanece uma evolução futura. Quando houver efeito colateral de negócio real, o store deverá ser implementado com uma operação persistente atomicamente protegida, por exemplo uma restrição única sobre `eventId`.

Isso mantém a decisão arquitetural independente da tecnologia de persistência.

## Fora do escopo

Esta decisão não transforma Kafka em exactly-once de negócio.

Idempotência de transporte e idempotência de negócio continuam sendo conceitos distintos. Uma operação de negócio pode precisar de uma chave idempotente própria mesmo quando o evento já foi aceito pelo middleware.

## Alternativas consideradas

### Idempotência dentro de cada consumer

**Rejeitada.** Espalha uma preocupação transversal e depende de disciplina manual para que cada novo consumer implemente corretamente a política.

### CDI interceptor em cada método `@Incoming`

**Rejeitada.** Exigiria uma marcação explícita por consumer e não aproveitaria diretamente o pipeline de Reactive Messaging.

### Filtro baseado somente no offset Kafka

**Rejeitada.** Offset representa progresso do consumer group na partição, não a identidade de uma ocorrência de evento nem a confirmação de que o efeito de negócio foi concluído.

## Evidência no código

- `IdempotencyMessagingDecorator` implementa o middleware.
- `ProcessedEventStore.tryClaim(UUID)` define o contrato atômico.
- `KafkaVehicleRegisteredConsumer` não depende mais do store de idempotência.
- Testes do middleware cobrem duplicação e liberação do claim em `nack`.
