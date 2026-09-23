# ADR — Importação reativa em lote no Inventory



## Status



Proposta



## Contexto



O bounded context Inventory/Fleet já é dono do agregado `Vehicle` e expõe um contrato gRPC de streaming bidirecional:



```

rpc add(stream InsertCarRequest) returns (stream CarResponse)

```



A implementação atual recebe um `Multi`, porém o fluxo de registro ainda é síncrono e o adapter de persistência é bloqueante. O objetivo é criar evidência executável para Quarkus Reactive sem vazar Mutiny ou preocupações de transporte para o domínio.



## Decisão de domínio



Nenhum novo agregado será criado. O agregado `Vehicle` continua sendo a fronteira de consistência. Registrar um veículo continua sendo comportamento de domínio representado por `Vehicle.register(...)`.



A nova capacidade é um workflow de aplicação: **importação em lote de veículos**. Não é uma nova entidade de domínio.



## Bounded context



Inventory / Fleet. A ownership permanece em `inventory-service`.



## Agregados e value objects



O agregado `Vehicle` e os value objects existentes continuam sendo a autoridade: `VehicleId`, `LicensePlate`, `VehicleSpecifications`, `VehicleLocation`, `VehicleDailyRate` e `OdometerReading`. Nenhum DTO de transporte ou entidade de persistência atravessa o domínio.



## Invariantes



O fluxo em lote deve preservar as invariantes do registro individual: veículo registrado inicia como `AVAILABLE`; identidade e placa continuam sob responsabilidade do domínio; dados opcionais são normalizados antes do domínio; cada item é persistido independentemente, salvo regra futura de atomicidade do lote; falha de um item não corrompe itens já concluídos; o domínio continua síncrono e independente de framework.



As preocupações reativas são políticas de execução, não invariantes de domínio.



## Caso de uso da aplicação



Introduzir `BulkRegisterVehicles`, recebendo `Multi<RegisterVehicle.Command>` e produzindo `Multi<Vehicle>`. O caso de uso recebe commands, invoca o comportamento de `Vehicle`, delega persistência por porta reativa, aplica concorrência limitada e preserva cancelamento e demanda downstream.



## Porta de saída reativa



A porta de persistência deve usar tipos reativos quando a operação for realmente assíncrona:



```java

Uni<Vehicle> save(Vehicle vehicle);

```



O adapter utilizará Hibernate Reactive/Panache e cliente SQL reativo. A aplicação pode usar `Uni`/`Multi` porque streaming e I/O assíncrono são requisitos reais.



## Adapter de entrada



O endpoint `add(Multi<InsertCarRequest>)` continua sendo a fronteira. O adapter traduz request para command, invoca o caso de uso e traduz `Vehicle` para `CarResponse`. O contrato gRPC não muda.



O adapter não decide concorrência, retry, persistência, transições de domínio ou regras de negócio.



## Política de concorrência



A primeira implementação usa concorrência explicitamente limitada por um `maxConcurrency = N` configurável e testável.



```text

Multi de entrada

      |

      | maxConcurrency = N

      v

+-----+-----+-----+-----+

| task | task | task | ... |

+-----+-----+-----+-----+

      |

      v

persistência reativa

```



## Backpressure



Backpressure é tratado separadamente de concorrência. A implementação deve demonstrar que o pipeline não materializa cegamente toda a entrada em memória e mantém limitado o trabalho em voo. Limitar concorrência, sozinho, não é sinônimo de backpressure.



## Política de falhas



A política inicial é fail-fast para o stream. Quando um item falha, a operação correspondente falha, a falha é propagada, o cancelamento downstream é respeitado e itens já concluídos permanecem persistidos. Retry por item e dead letter pertencem a fatias posteriores.



## Política de bloqueio



O objetivo é um caminho não bloqueante:



```text

gRPC event loop

      |

      v

Multi/Uni da aplicação

      |

      v

Hibernate Reactive

      |

      v

cliente PostgreSQL reativo

```



`@Blocking` não deve permanecer no método final apenas por herança da implementação legada. Se uma operação bloqueante permanecer durante a migração, ela deve ser isolada e documentada como restrição do adapter.



## Cenários orientados a testes



### Aplicação



`BulkRegisterVehiclesTest` deve demonstrar registro dos commands, limite de concorrência, propagação de falha, cancelamento e ausência de materialização antecipada de toda a entrada.



### Domínio



Não são necessários novos testes de domínio, salvo se a implementação revelar uma invariante ausente em `Vehicle`.



### Adapter/integração



Os testes devem demonstrar streaming gRPC, mapeamento protobuf → aplicação, persistência reativa sem bloquear o event loop e quantidade esperada de veículos persistidos.



## Atalhos proibidos



Não colocar `Uni` ou `Multi` no domínio; não criar segundo agregado `Vehicle`; não chamar de reativa uma chamada bloqueante simplesmente embrulhada em `Uni`; não usar merge sem limite e chamá-lo de backpressure; não mover regras de negócio para o adapter; não introduzir retry/dead letter nesta fatia; não alterar o protobuf sem necessidade do caso de uso.



## Conceitos de Quarkus demonstrados



Mutiny `Multi`, composição de `Uni`, gRPC streaming, segurança do event loop, persistência reativa, concorrência controlada, backpressure, cancelamento e propagação de falhas.



## Alterações de documentação



Após a implementação, atualizar `docs/architecture.md`, `docs/services.md` e `docs/testing.md` com a evidência correspondente. Alterar `docs/ddd-tdd-standards.md` somente se surgir uma nova convenção reativa de repositório. Marcar o roadmap somente após evidência executável.



## Questões em aberto



Nenhuma bloqueia a primeira fatia. O valor inicial de concorrência, timeouts e futura estratégia de retry podem ser refinados após medições.