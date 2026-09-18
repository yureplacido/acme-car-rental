# inventory-proto

Contrato gRPC do `InventoryService` (schema-first). Este módulo é um **artefato de contrato**:
só carrega o `inventory.proto` empacotado no jar. Não gera código — os consumidores geram os
stubs a partir dele na própria build.

## Uso

```xml
<dependency>
    <groupId>org.acme</groupId>
    <artifactId>inventory-proto</artifactId>
    <version>1.0.0</version>
</dependency>
```

Consumidores com `quarkus-grpc` ativam a geração a partir da dependência:

```properties
quarkus.generate-code.grpc.scan-for-proto=true
quarkus.generate-code.grpc.scan-for-proto-include."org.acme:inventory-proto"=true
```

Instale uma versão nova antes de compilar os serviços (não há reactor):

```
./mvnw install -DskipTests
```

## Evolução do contrato (sem quebrar sistemas em execução)

Compatibilidade wire é definida pelo **número do campo** e pelo **nome do método**
(path gRPC = `package.Service/Method`).

| Mudança | Quebra wire? | Observação |
|---|---|---|
| Adicionar campo novo (número novo) | Não | Compat forward: client velho ignora; server velho devolve default |
| Adicionar método novo no serviço | Não | Client velho não chama; server novo precisa implementar |
| Renomear campo/mensagem | Não no wire / **sim** em clientes compilados | Getters mudam; evite |
| Mudar tipo de campo | Sim | Proibido |
| Renumerar/reutilizar número de campo | Sim | Proibido; ao remover, usar `reserved` |
| Remover campo sem `reserved` | Sim | `reserved 2; reserved "nome";` |
| Mudar package/service/method name | Sim | Altera o path no wire |
| unary ↔ streaming | Sim | Muda o kind do método no descriptor |

Regras:
1. Nunca reutilize número de campo; delete sempre com `reserved`.
2. Prefira evoluir no lugar (campos/métodos novos) a versionar no nome.
3. Mudança estrutural → contrato `v2` (ex. `inventory.v2.InventoryService`) e
   `option deprecated = true;` no antigo.
4. Rollout server-first: o server sobe primeiro; em mudanças aditivas, client velho
   + server novo funciona (o inverso não).
5. **Versão publicada é imutável**: cada serviço pinna a versão que consome e sobe no seu
   ritmo. Nunca edite um proto já publicado.