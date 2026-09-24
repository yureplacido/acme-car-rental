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

**Instalação** (não há reactor — instale antes de compilar consumidores):

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

Payload (`version = 1`):

```json
{
  "eventId": "uuid",
  "version": 1,
  "occurredAt": "2026-09-21T12:00:00Z",
  "vehicleId": 42,
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
- Pendente de pipeline (documentar quando houver): consumer/requeue da DLQ, schema registry, outbox/inbox persistente.

## Novos contratos (futuro)

- 🔜 **Eventos de cobrança (Reservation/Rental → Billing)**: ex.: `Reservation.confirmed`,
  `Rental.completed`, `Fatura` — registrar aqui quando surgirem.

---

_Atualize este arquivo sempre que um contrato nascer ou evoluir (veja [README.md](./README.md))._
