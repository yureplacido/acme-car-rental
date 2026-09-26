# 02 — Comunicação entre serviços

> Capítulo 4 do *Quarkus in Action*.
> Fato operacional em [contracts.md](../contracts.md) e [architecture.md](../architecture.md).

## 1. O que o livro ensina

O capítulo 4 percorre quatro tecnologias e pergunta, para cada uma, **quando usar**:

| Tecnologia | Quando o livro indica | Característica decisiva |
|---|---|---|
| REST (JAX-RS / Quarkus REST) | CRUD e integração simples | ubérquico, verbos HTTP, contrato implícito |
| REST client | consumir outro serviço REST tipado | mesma linguagem da ponta que expõe |
| GraphQL | o consumidor precisa de **campos diferentes** dos que outro consumidor precisa | schema é contrato; o cliente escolhe o que quer |
| gRPC | alta vazão, streaming, Mobile/IoT, contrato forte | schema-first, protobuf, bidirecional |

O erro clássico de quem vem de um único protocolo é usar **um** protocolo para tudo.
O exercício do capítulo é exatamente esse: o mesmo domínio (`Inventory`/`Reservation`)
exposto por três mecanismos diferentes, para três consumidores diferentes.

## 2. O que o repositório fez

### 2.1 Mapa protocolo → consumidor

| Consumidor | Protocolo | Por quê |
|---|---|---|
| `users-service` → `reservation-service` | REST | fluxo de UI, precisa de OpenAPI legível |
| `reservation-service` → `rental-service` | REST (`RentalClient` → `RentalRestGateway`) | confirma/inicia a locação no contexto Rental |
| `reservation-service` → `inventory-service` | GraphQL client (`allCars`) | precisa de um recorte específico do catálogo |
| `users-service`, `reservation-service` → Keycloak | OIDC | `web_app` e `service`, respectivamente |
| `inventory-cli` → `inventory-service` | gRPC | cliente administrativo, contrato forte, bulk |
| `rental-service` | REST in | API de negócio simples |
| `billing-service` | Kafka in ×3 + out ×1 | consistência assíncrona, ver [08](./08-messaging-reativo.md) |
| `inventory-service` | Kafka out (`vehicle-registered-out`) | publica `VehicleRegistered` |

### 2.2 A regra que realmente importa aqui

🔀 **Divergência grande em relação ao livro.** O capítulo escreve recursos REST/GraphQL/gRPC
que falam **direto com repositório Panache** e devolve entidade de persistência no JSON.
Neste repositório isso é proibido:

```text
Adapter Inbound
     ↓  (traduz transporte → comando/query de aplicação)
Application Use Case
     ↓
Domain
     ↑  (porta de saída)
Outbound Adapter
```

Consequências que aparecem em `inventory-service`:

- `adapter/in/graphql/model/` tem DTOs **de transporte** (`Car`, `CarFilter`, `Page`,
  `CarSortField`). São do GraphQL, não do domínio.
- O filtro/ordenação/paginação vira **query de aplicação**
  (`VehicleSearch` → `SearchVehicles` → `VehiclePage`), não lógica do resource.
- A entidade de persistência **nunca** aparece no payload GraphQL ou REST.

### 2.3 gRPC schema-first com artefato de contrato

O `inventory-proto` é um módulo **só de contrato**: contém `inventory.proto` e **não gera
código**. Cada consumidor gera seus próprios stubs na própria build:

```properties
# inventory-service/src/main/resources/application.properties
quarkus.generate-code.grpc.scan-for-proto=org.acme:inventory-proto
quarkus.grpc.server.enable-reflection-service=true
```

⚠️ `scan-for-proto` recebe a **coordenada GAV** (`group:artifact`), não `true`.
A reflexão do servidor existe para permitir `grpcurl` e a Dev UI descobrirem métodos sem
cópia local do `.proto`.

O serviço expõe **streaming bidirecional**:

```proto
service InventoryService {
  rpc add(stream InsertCarRequest) returns (stream CarResponse) {}
  rpc remove(RemoveCarRequest) returns (CarResponse) {}
}
```

Esse `add` bidirecional é o que sustenta a ingestão em lote com
`inventory.bulk.max-concurrency=4` — ver [07-programacao-reativa.md](./07-programacao-reativa.md).

## 3. Contrato como unidade de evolução

Cada protocolo tem uma regra de compatibilidade diferente, e misturá-las é a fonte número
um de break em produção:

| Protocolo | Regra prática de compatibilidade |
|---|---|
| REST | adicionar campo de resposta é seguro; **remover** ou mudar tipo não é |
| GraphQL | o schema é versionado por *depreciação de campo*, não por URL |
| gRPC/protobuf | adicionar `field N` é seguro; **renomear**, **remover** ou mudar tipo não é |
| Kafka (cap. 9) | adicionar campo no payload é seguro; quebrar o `eventId`/chave não é |

A regra do repositório: contrato muda → [contracts.md](../contracts.md) muda no mesmo commit.

## 4. Onde a comunicação vira problema: o adapter outbound

O livro trata clientes REST/GraphQL/gRPC como "chamada de outro serviço". Aqui eles são
**ports + adapters**, e a diferença é testável:

```java
// application/port/out — o caso de uso não conhece protocolo
public interface ReservationsGateway {
    Collection<ReservationView> allReservations();
    ReservationView create(ReservationView reservation);
    Collection<AvailableCar> availability(LocalDate start, LocalDate end);
}
```

- `users-service`: adapter REST (`ReservationsRestGateway`).
- Teste de aplicação: **fake** implementando a porta, sem HTTP
  (`users-service/src/test/java/org/acme/users/application/ReservationFacadeTest.java`).

Isso é o que permite testar orquestração em JVM puro. Ver
[04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md).

## 5. Checklist de estudo

- [ ] Sei justificar por que o projeto não expõe entidade de persistência em GraphQL/REST.
- [ ] Sei dizer o que `scan-for-proto` recebe e o que acontece se eu passar `true`.
- [ ] Sei explicar por que `inventory-proto` não gera código.
- [ ] Sei listar as regras de compatibilidade dos 4 protocolos deste projeto.
- [ ] Sei apontar, no código, a porta de saída e o adapter que a implementa.

## 6. Referências de código

| Assunto | Arquivo |
|---|---|
| Contrato gRPC | `inventory-proto/src/main/proto/inventory.proto` |
| Resource GraphQL + DTOs de transporte | `inventory-service/src/main/java/org/acme/inventory/adapter/in/graphql/` |
| Cliente GraphQL outbound | `reservation-service/src/main/java/org/acme/reservation/adapter/out/inventory/` |
| Adapter gRPC do CLI | `inventory-cli/src/main/java/org/acme/inventory/cli/adapter/out/grpc/InventoryGrpcGateway.java` |
| Porta de saída + fake | `ReservationsGateway` (abaixo) |

A porta de saída do `users-service` é uma interface que não menciona REST nem Jackson —
é o contrato que o `ReservationFacade` consome e que o teste substitui por fake:

```java
public interface ReservationsGateway {
    Collection<ReservationView> allReservations();
    ReservationView create(org.acme.users.application.model.ReservationView reservation);
    Collection<AvailableCar> availability(LocalDate startDate, LocalDate endDate);
}
```

**Veja também:** [08-messaging-reativo.md](./08-messaging-reativo.md) ·
[10-exemplos-contrarios-ao-dominio.md](./10-exemplos-contrarios-ao-dominio.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md)

---

_Última atualização: 2026-09-26 (cap. 4)._
