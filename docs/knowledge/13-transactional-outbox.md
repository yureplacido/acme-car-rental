# 13 — Transactional Outbox na prática

> Material de estudo baseado na implementação real do billing-service.

## Problema

Uma operação de negócio pode precisar alterar o banco e publicar um evento no Kafka. São dois sistemas sem uma transação ACID comum. Se o banco confirmar e o processo cair antes da publicação, o evento pode ser perdido.

O Transactional Outbox move o evento para o mesmo limite transacional do agregado:

    Business operation
          |
          +---- aggregate state
          |
          +---- outbox_event
                    |
                    v
               Outbox Relay
                    |
                    v
                   Kafka

A consistência entre banco e broker continua eventual, mas a publicação pode ser recuperada.

## Implementação do projeto

O fluxo atual é:

    OpenInvoiceForRental
           |
           v
    saveOpenedWithOutbox
           |
           +--> Invoice
           |
           +--> OutboxEvent
                    |
                    v
          PublishPendingOutboxEvents
                    |
                    v
             EventPublisher
                    |
                    v
        InvoiceOpenedKafkaPublisher
                    |
                    v
                  Kafka

O caso de uso depende da porta EventPublisher. Kafka permanece como adapter de infraestrutura.

## Estado do outbox

Um registro contém eventId, eventType, aggregateType, aggregateId, payload, occurredAt, attempts e publishedAt.

publishedAt nulo significa que o evento continua pendente. O eventId deve permanecer estável entre tentativas.

## Ordem de publicação

A ordem correta é:

    publishKafka()
         |
       sucesso
         v
    markPublished()

Nunca devemos marcar o evento como publicado antes de confirmar o sucesso da publicação.

Caso o processo caia depois do publish e antes de markPublished, o evento poderá ser publicado novamente. Essa é uma consequência esperada da semântica at-least-once.

## Processamento sequencial

O relay atual processa o batch sequencialmente:

    event 1 -> publish -> mark
    event 2 -> publish -> mark
    event 3 -> publish -> mark

Se um evento falhar:

1. attempts é incrementado;
2. o evento permanece pendente;
3. a execução do batch é interrompida;
4. uma execução posterior pode tentar novamente.

Essa escolha simplifica o raciocínio sobre falhas e ordenação dentro do batch.

## Scheduler

OutboxRelay é disparado periodicamente com @Scheduled e concurrentExecution = SKIP.

SKIP evita sobreposição entre execuções do scheduler na mesma instância. Isso não resolve coordenação entre múltiplas instâncias. Em escala horizontal, duas instâncias ainda podem encontrar o mesmo evento pendente. Uma evolução possível é usar claim/locking, por exemplo SELECT FOR UPDATE SKIP LOCKED ou leases.

## Retry

attempts registra a quantidade de tentativas, mas não é uma política completa de retry.

Uma evolução de produção pode adicionar nextAttemptAt, backoff exponencial, jitter, limite de tentativas, classificação de erro transitório/permanente e uma estratégia para eventos que não podem ser publicados.

Retry infinito para uma mensagem permanentemente inválida é um antipadrão operacional.

## At-least-once e idempotência

O Outbox não fornece exactly-once ponta a ponta.

Existe uma janela:

    Kafka publish
         |
         X  processo cai
         |
    publishedAt ainda nulo

Na próxima execução o mesmo evento pode ser publicado novamente.

Por isso, consumidores precisam ser idempotentes. No projeto, o Transactional Inbox trata essa responsabilidade no lado consumidor.

## Versionamento do evento

O payload persistido precisa continuar interpretável depois que o evento foi criado.

Eventos devem ser modelos explícitos, não serializações acidentais de entidades. Quando a evolução exigir, o contrato pode carregar uma versão explícita.

Exemplo conceitual:

    eventType = InvoiceOpened
    eventVersion = 1
    payload = {...}

## Teste E2E

OutboxRelayKafkaIntegrationTest comprova:

    CreateInvoice
         |
    OpenInvoiceForRental
         |
         +--> invoice
         +--> outbox_event
                  |
                  v
             OutboxRelay
                  |
                  v
                Kafka

O teste captura o offset atual antes do relay e consome somente mensagens posteriores a esse offset. Além disso, filtra pelo invoiceId.

Isso é mais robusto do que apagar/recriar o tópico ou assumir que a primeira mensagem pertence ao teste.

## Testes reativos e Kafka Companion

As APIs síncronas do Kafka Companion são úteis, mas continuam sendo bloqueantes.

Não se deve executar uma chamada bloqueante no Vert.x event loop.

O padrão utilizado no teste é:

    event loop
        |
        v
    worker thread
        |
        +--> blocking Kafka Companion call
        |
        v
    event loop

Com Mutiny, runSubscriptionOn(Infrastructure.getDefaultExecutor()) desloca a operação bloqueante para um worker.

A lição é geral: uma API síncrona continua bloqueante mesmo quando chamada dentro de um teste reativo.

## O que o Outbox resolve

- dual-write entre banco e broker;
- perda do evento depois do commit do agregado;
- desacoplamento entre transação e disponibilidade do Kafka;
- retry de publicação;
- recuperação após restart.

## O que ele não resolve sozinho

- exactly-once ponta a ponta;
- deduplicação do consumidor;
- ordenação global;
- coordenação de múltiplos relays;
- poison messages;
- schema evolution;
- observabilidade;
- alta disponibilidade do broker.

## Evoluções naturais

1. claim concorrente para múltiplas instâncias;
2. backoff e jitter;
3. métricas de idade do evento e taxa de falha;
4. tracing do eventId;
5. política para eventos presos;
6. particionamento por aggregateId quando ordenação for necessária;
7. schema/versionamento formal dos eventos.

## Regra central

Transactional Outbox não elimina a distribuição.

Ele transforma:

    DB ----X---- Kafka

em:

    DB
     |
     +--> Outbox --> Relay --> Kafka

O problema deixa de ser uma tentativa impossível de fazer dois sistemas participarem da mesma transação e passa a ser uma operação recuperável, repetível e observável.
