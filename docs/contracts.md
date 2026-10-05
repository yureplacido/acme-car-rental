# Contratos

> **Fonte da verdade:** código + docs/domain.md + docs/ddd-tdd-standards.md.

Registro de contratos entre serviços e suas regras de evolução. Hoje existe um único
contrato gRPC (inventory-proto); novos contratos (ex.: mensageria cap.9) entram aqui.

## inventory-proto (gRPC) ✅

**GAV:** `org.acme:inventory-proto:1.0.0-SNAPSHOT` · **Dir:** `inventory-proto/`

Artefato de **contrato** (schema-first): só empacota `src/main/proto/inventory.proto`
no jar. **Não gera código** — cada consumidor (server ou client gRPC) gera os próprios
stubs a partir dele na própria build.

```xml
<dependency>
    <groupId>org.acme</groupId>
    <artifactId>inventory-proto</artifactId>
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

Geração habilitada nos consumidores:

```properties
quarkus.generate-code.grpc.scan-for-proto=org.acme:inventory-proto
```

> ⚠️ O valor de `scan-for-proto` é a **coordenada GAV**, não `true`. Com `true` a geração
> falha na build.

### Contrato (`package inventory`)

```proto
service InventoryService {
  rpc add(stream InsertCarRequest) returns (stream CarResponse) {}  // bidirecional
  rpc remove(RemoveCarRequest) returns (CarResponse) {}              // unary
}
```

Campos aditivos (cap.7, refinamento do domínio) — números novos, sem quebra de wire:

- `InsertCarRequest`: `color=4`, `year=5`, `category=6`, `transmission=7`,
  `fuel_type=8`, `seats=9`, `daily_rate=10` (todos opcionais).
- `CarResponse`: `status=5`, `color=6`, `year=7`, `category=8`, `transmission=9`,
  `fuel_type=10`, `seats=11`, `daily_rate=12`.

> ⚠️ **Semântica do `remove`:** baixa (descomissiona) o veículo — **soft delete** por
> `status`, a linha nunca é apagada (reservas podem referenciar o id). Resposta vazia
> quando a placa não existe. (Antes o método era read-only; desde o cap.7 é efetivo.)

Consumidores hoje: `inventory-service` (server) e `inventory-cli` (client).

**Instalação.** `inventory-proto` é o **primeiro módulo do reactor** (`pom.xml` raiz), então
o build agregado resolve o artefato sem instalação manual:

```
./mvnw -pl inventory-cli -am test    # '-am' também constrói o inventory-proto
```

Se você quiser trabalhar **só** no contrato, isolado do reactor:

```
cd inventory-proto && ./mvnw install -DskipTests
```

**Versionamento:** versão publicada é **imutável**. Cada serviço pinna a versão que
consome e sobe no seu ritmo. Mude para `-SNAPSHOT`/nova versão ao evoluir.

## Matriz de superfícies — canais do inventário (ADR 10)

O inventário não expõe REST de domínio. GraphQL e gRPC são adapters independentes que chamam os casos de uso da aplicação.

| Operação | GraphQL (`/graphql`) | gRPC | Por quê |
|---|---|---|---|
| Consultar/listar/paginar carros (filter, sort, projeção) | ✅ | — | UI e inter-service (reservation) precisam de consulta rica |
| `findCar(plate)` (inclui baixados) | ✅ | — | Admin via UI |
| `register(car)` / `add(stream)` | ✅ | ✅ | Inserção pontual (UI) e ingestão em lote (CLI) |
| `decommission(plate)` (`remove`) | ✅ | ✅ | Governança e admin via máquina-a-máquina |
| Streaming/ingestão em lote | — | ✅ | GraphQL não suporta streaming; gRPC sim |

> Consistência entre serviços é **eventual**: inventário (dono do catálogo), reservation
> (reservas) e rental (aluguéis) têm bancos próprios e conversam por API; o inventário
> nunca apaga veículos referenciáveis (soft delete).

## Regras de compatibilidade (evolução sem quebrar sistemas em execução)

Compatibilidade wire é definida pelo **número do campo** e pelo **nome do método**
(path gRPC = `package.Service/Method`).

| Mudança | Quebra wire? | Observação |
|---|---|---|
| Adicionar campo novo (número novo) | Não | Forward-compat: client velho ignora; server velho devolve default |
| Adicionar método novo no serviço | Não | Client velho não chama; server novo deve implementar |
| Renomear campo/mensagem | Não no wire / **sim p/ clientes compilados** | Getters mudam; evite |
| Mudar tipo de campo | Sim | Proibido |
| Renumerar/reutilizar número | Sim | Proibido; ao remover, usar `reserved` |
| Remover campo sem `reserved` | Sim | `reserved 2; reserved "nome";` |
| Mudar package/service/method | Sim | Altera o path no wire |
| unary ↔ streaming | Sim | Muda o kind do método no descriptor |

### Regras de ouro

1. Nunca reutilize número de campo; delete sempre com `reserved`.
2. Prefira evoluir no lugar (campos/métodos novos) a versionar no nome.
3. Mudança estrutural → contrato `v2` (ex.: `inventory.v2.InventoryService`) e
   `option deprecated = true;` no antigo.
4. **Rollout server-first**: o server sobe primeiro. Em mudanças aditivas, client velho +
   server novo funciona (o inverso não).
5. Versão publicada é imutável: nunca edite um proto já publicado.

## VehicleRegistered (Kafka) ✅

**Fluxo:** inventory-service (publisher) → tópico `vehicle-registered` → billing-service (consumer).

Não há artefato de contrato separado: cada contexto mantém a própria cópia anti-corruption do
evento (regra DDD — nunca compartilhar classes entre bounded contexts).

| Aspecto | Valor |
|---|---|
| Tópico | `vehicle-registered` |
| Transporte | Kafka (SmallRye Reactive Messaging) |
| Publisher | inventory-service (`vehicle-registered-out`) |
| Consumer | billing-service (`vehicle-registered-in`, group `billing-service`) |
| Encoding | JSON (String serializer/deserializer) |
| Chave de idempotência | `eventId` (UUID) |
| Header de contexto | `traceparent` (W3C) em todo record quando o publisher tem `quarkus-opentelemetry` (cap.10 item 7) — **não faz parte do payload nem do `version`**; o consumidor herda o span do record como pai. Propagação automática, guia Messaging "OpenTelemetry Tracing" |

Payload (`version = 1`):

```json
{
  "eventId": "uuid",
  "version": 1,
  "occurredAt": "2026-09-21T12:00:00Z",
  "vehicleId": { "value": 42 },
  "licensePlate": "ABC123"
}
```

Regras de evolução:

- Mudanças aditivas (campos novos opcionais) preservam o `version` enquanto não quebrarem consumidores.
- Mudança com potencial de quebra → incrementa `version` e trata campos obsoletos como desconhecidos.
- Consumidores não implementam idempotência individualmente: a política de deduplicação é aplicada pelo
  middleware de messaging através de `ProcessedEventStore.tryClaim(eventId)`.
- O middleware considera `eventId` obrigatório no envelope JSON dos eventos de integração.
- Retry de processamento é aplicado na infraestrutura de messaging (estratégia `delayed-retry-topic`, ver ADR 002).
- Após o esgotamento dos retries, o record é roteado para a **DLQ** `vehicle-registered-dlq`
  (`mp.messaging.incoming.vehicle-registered-in.dead-letter-queue.topic`, ver ADR 003), com chave,
  payload e headers de diagnóstico preservados.
- Pendente de pipeline (documentar quando houver): consumer/requeue da DLQ e schema registry.

## Disponibilidade de veículos (HTTP, reservation-service) ✅

`GET /reservations/availability?startDate=YYYY-MM-DD&endDate=YYYY-MM-DD`

Este endpoint gained um contrato de falha no cap. 10 item 8: quando o `inventory-service` está
inacessível, a resposta é **503**, não 200 com lista vazia. Lista vazia significa "nenhum veículo
disponível" e é um fato de negócio; quando a consulta não pode ser feita, o resultado é
inconclusivo e o cliente precisa saber disso (decisão em
[adr/009-fault-tolerance-chamadas-externas.md](adr/009-fault-tolerance-chamadas-externas.md)).

```text
200  [ { "id", "licensePlateNumber", "manufacturer", "model" } ]
503  {
       "code": "INVENTORY_UNAVAILABLE",
       "message": "vehicle inventory is temporarily unavailable",
       "retryAfterSeconds": 30
     }     + header Retry-After: 30
```

Regras para quem consome:

- **200 com `[]`** continua sendo resultado válido e significa "não há veículo nesse período".
- **503 significa "não deu para saber"**, não "não há veículo". O corpo é estável e não contém
  detalhe de infraestrutura (host, porta, stack); o diagnóstico fica no log do serviço.
- O header `Retry-After` informa quanto esperar antes de tentar de novo.
- **503 cobre exatamente três causas**, todas medidas contra o cliente real
  (`GraphQLInventoryClientFailureTest`): deadline estourado, conexão recusada/resetada e resposta
  HTTP sem envelope GraphQL (o inventory reiniciando, um proxy no meio, uma URL errada).
- Erro de GraphQL (o inventário respondeu 200 com `errors`) **não** é retry nem
  indisponibilidade: é defeito do inventário, e o serviço responde com o erro correspondente, sem
  mascarar como 503.
- Como consequência da última mas uma: uma URL de catálogo errada (404) é tratada como
  indisponibilidade e repetida antes do 503, porque o cliente não tipa o status HTTP.

Evidência: `AvailabilityUnavailableTest` (3 cenários) e `GraphQLInventoryGatewayFaultToleranceTest`.

---

## Novos contratos (futuro)
 
- 🔜 **Eventos de cobrança (Reservation/Rental → Billing)**: `ReservationConfirmed`/`RentalCompleted`
  em `billing-service` (`application/event`); consumidos nos tópicos `reservation-confirmed`/`rental-completed`
  (canal `reservation-confirmed-in`/`rental-completed-in`, group `billing-service`). As regras de evolução acima se aplicam; cada consumidor usa `TransactionalInboxProcessor` para claim + efeito na mesma transação, além de retry (delayed-retry-topic) + DLQ.
  **Nota:** producers em `reservation-service` e `rental-service` ainda não implementados — apenas test harness publica (`BillingFlowKafkaIntegrationTest`).
- ✅ **`InvoiceOpened` (Billing → outros contextos)**: evento em
  `billing-service/.../application/event/InvoiceOpened.java`, publicado por
  `InvoiceOpenedKafkaPublisher` no canal `invoice-opened-out` (tópico `invoice-opened`),
  chave = `aggregateId` (o `invoiceId`) para preservar ordenação por agregado.
  A publicação é **at-least-once**: vem do relay da outbox transacional
  (`OutboxRelay` → `OutboxEventStore` → `EventPublisher`), então o consumidor **deve** ser
  idempotente por `eventId`. Consumidor: 🔜 nenhum contexto consome este evento hoje —
  o único produtor em execução é o relay.
  Evidência: `OutboxRelayKafkaIntegrationTest`, `BillingOutboxIntegrationTest`.

---

_Atualize este arquivo sempre que um contrato nascer ou evoluir (veja [README.md](./README.md))._

---
_Last updated: 2026-10-05 (contrato de disponibilidade do reservation: 503 com corpo estável e `Retry-After` quando o inventory está inacessível, em vez de 200 com lista vazia)._
