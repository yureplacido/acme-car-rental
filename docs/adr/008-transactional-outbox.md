# ADR 008 — Outbox transacional para publicação de eventos

- **Status:** Proposed
- **Data:** 2026-09-24
- **Contexto:** Capítulo 9 — Quarkus Messaging
- **Escopo:** billing-service e futuros produtores que precisem publicar eventos derivados de estado persistente

## Contexto

O Transactional Inbox resolve a consistência do lado consumidor: o claim do evento recebido e o efeito de negócio local participam da mesma transação.

No lado produtor existe o problema inverso. Um caso de uso pode precisar persistir uma mudança de estado e publicar um evento. Se banco e Kafka forem tratados como operações independentes, existe uma janela de inconsistência:

```text
BEGIN
  ↓
UPDATE domain state
  ↓
COMMIT
  ↓
publish Kafka
  ↓
crash
```

Nesse cenário o estado local foi confirmado, mas o evento pode nunca ser publicado.

Fazer `DB transaction + Kafka publish` como uma única transação distribuída não é o objetivo deste laboratório. Queremos uma fronteira local, durável e observável entre o estado de negócio e a publicação assíncrona.

## Decisão

Adotar o **Transactional Outbox Pattern**.

O produtor grava, na mesma transação local do efeito de negócio, uma mensagem em uma tabela de outbox. Um processo assíncrono posterior lê registros pendentes e publica os eventos no Kafka.

```text
                 LOCAL TRANSACTION
                       │
             ┌─────────┴─────────┐
             ↓                   ↓
       Business state        Outbox event
             │                   │
             └─────────┬─────────┘
                       ↓
                    COMMIT
                       │
                       ↓
                Outbox publisher
                       │
                       ↓
                     Kafka
```

A publicação não precisa ocorrer na mesma thread nem na mesma transação do caso de uso. A garantia desejada é:

- se o estado de negócio foi confirmado, o registro da outbox também foi confirmado;
- se a transação falhar, estado e outbox são revertidos juntos;
- se Kafka estiver indisponível, o evento permanece persistido para nova tentativa;
- publicação duplicada é possível e deve ser tratada pelo consumidor com idempotência;
- o outbox não promete exactly-once global.

## Modelo inicial

A tabela de outbox deverá conter, no mínimo:

- `event_id`: identificador único e idempotente do evento;
- `event_type`: tipo lógico do contrato;
- `aggregate_type`: tipo do aggregate produtor;
- `aggregate_id`: identificador do aggregate;
- `payload`: representação serializada do evento;
- `occurred_at`: instante em que o evento foi criado;
- `published_at`: instante da publicação confirmada;
- `attempts`: quantidade de tentativas de publicação.

O registro permanece pendente até que o publisher obtenha confirmação do broker.

## Publicação e falhas

O publisher deve tratar Kafka como uma fronteira externa:

```text
PENDING
   │
   ├── publish OK ──────→ PUBLISHED
   │
   └── publish FAIL ────→ PENDING + retry
```

A falha de publicação não pode remover o evento da outbox.

Como Kafka e PostgreSQL não compartilham uma transação global, uma queda depois da publicação e antes da atualização de `published_at` pode causar publicação duplicada. Por isso `event_id` precisa ser estável e os consumidores devem permanecer idempotentes.

## Por que começar pelo Billing

Billing já possui:

- eventos inbound;
- inbox transacional;
- PostgreSQL;
- contratos de eventos;
- infraestrutura Kafka;
- testes de integração com broker real.

O próximo caso de uso produtor pode, portanto, demonstrar o problema de consistência sem introduzir outro bounded context antes de termos a infraestrutura comprovada.

A primeira implementação deve ser pequena e orientada por teste, evitando transformar o outbox em uma infraestrutura genérica antes de provar o comportamento.

## Consequências

### Positivas

- elimina a janela entre commit do estado local e registro da intenção de publicação;
- permite retry sem depender do produtor original estar vivo;
- mantém a transação limitada ao PostgreSQL local;
- torna a intenção de publicação durável e observável;
- combina naturalmente com o Transactional Inbox no consumidor.

### Limitações

- não existe exactly-once global;
- pode haver eventos duplicados;
- existe latência entre o commit local e a publicação;
- o publisher precisa de uma política de retry e recuperação de registros pendentes;
- limpeza/retention da outbox será uma preocupação operacional futura.

## Evidência esperada

Antes de marcar esta decisão como implementada, o laboratório deve demonstrar por testes:

1. estado de negócio e outbox são confirmados juntos;
2. falha na transação não deixa outbox órfã;
3. evento pendente é publicado posteriormente;
4. falha de Kafka mantém o evento pendente;
5. uma publicação repetida preserva o mesmo `event_id`;
6. a integração Outbox → Kafka → Inbox mantém idempotência no consumidor.

## Relação com o Inbox

Outbox e Inbox tratam lados diferentes da mesma fronteira:

```text
PRODUCER                              CONSUMER

DB ──┐                                Kafka
     ├─ transaction                  │
Outbox┘                               ↓
     │                              Inbox
     ↓                                │
Publisher                             ├─ transaction
     │                                │
     ↓                                ↓
   Kafka ───────────────────────→ Business effect
```

A combinação reduz as janelas de inconsistência locais, mas não transforma sistemas distribuídos em uma única transação.

## Estado da decisão

Esta ADR permanece **Proposed** até que a implementação mínima e os testes de integração forneçam a evidência definida acima.
