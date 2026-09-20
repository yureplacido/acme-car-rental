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

## Novos contratos (futuro)

- 🔜 **Billing/messaging (cap.9)**: schemas de eventos (ex.: `ReservaPaga`, `Fatura`)
   e definição de fila/tópico (Kafka ou RabbitMQ) — registrar aqui quando surgir.

---

_Atualize este arquivo sempre que um contrato nascer ou evoluir (veja [README.md](./README.md))._