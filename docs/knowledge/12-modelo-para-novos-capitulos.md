# 12 — Modelo para novos capítulos

> **Template.** Use este arquivo como roteiro toda vez que ler e implementar um novo
> capítulo do *Quarkus in Action*.
> Se um item não se aplicar, escreva **"não se aplica"** e diga por quê — não apague a linha.

## Como usar

1. Copie as seções abaixo para um arquivo novo: `NN-<tema>.md`.
2. Preencha **durante** a leitura, não depois. O objetivo do arquivo é virar material de
   estudo, e estudo anotado depois é decoreba.
3. Se algo divergir do padrão do projeto, crie/atualize a **ADR** correspondente.
4. Atualize o índice em [README.md](./README.md) e o status em [roadmap.md](../roadmap.md).
5. Rode os guardians (`/ddd-audit`, `/tdd-audit`, `/quarkus-audit`, `/architecture-audit`).

---

## 0. Cabeçalho

```markdown
# NN — <Tema>

> Capítulo X do *Quarkus in Action*.
> Norma do projeto: docs/ddd-tdd-standards.md §<seção relevante>.
> Última atualização: <AAAA-MM-DD> (cap. X).
```

---

## 1. Conceitos do capítulo

Liste os conceitos **com o nome que o livro usa**, porque é assim que você vai procurar
depois. Para cada um:

```markdown
### 1.1 <Nome do conceito>

- **O que é:** uma frase.
- **Por que existe:** o problema que ele resolve.
- **Quando usar / quando não usar:** a decisão.
- **Armadilha clássica:** o erro que todo mundo comete.
```

⚠️ Regra: **síntese, não transcrição**. Se você colar o texto do livro, a página deixa de ser
material de estudo em 3 meses. Escreva com suas palavras e use no máximo um trecho curto
como citação.

---

## 2. O que o repositório fez

### 2.1 Mapa capítulo → código

| Conceito | Onde está no código | Teste que prova |
|---|---|---|
| ... | `modulo/src/main/java/.../Xyz.java` | `modulo/src/test/java/.../XyzTest.java` |

> Se um conceito do livro **não** foi implementado, a linha continua existindo com
> `🔜` e o motivo. Tabela incompleta é informação perdida.

> ⚠️ **A tabela acima é só o índice.** Ela **não** substitui o trecho de código: em
> seguida, cada conceito da tabela é detalhado em uma subseção com o **código real
> embutido no arquivo**. Ver §2.1.1.

### 2.1.1 Como referenciar código (regra do repositório)

🔴 **Regra:** código é **copiado** para dentro do `.md` como bloco `java`. **Nunca** apenas
linkado.

| ✅ | ❌ |
|---|---|
| Trecho real, com o nome do arquivo em texto ao lado | Link relativo para `src/main/java/...` |
| Comentário do autor explaining o *porquê* | `veja o código em Xyz.java` |
| Funciona com o repositório fechado | Só funciona com o repo aberto e o path certo |

**Por quê:** o objetivo da família `knowledge/` é ser material de estudo autonomous. Um
leitor offline, ou daqui a dois anos, não consegue seguir um link para um `.java` que já
mudou — e o trecho no documento envelhece bem mais devagar que o arquivo.

Formato de cada subseção:

````markdown
### 2.N <Conceito>

`caminho/da/classe.java` — o que este trecho resolve:

```java
// trecho real, sem elipses
```
````

Regras de fidelidade do trecho:

- **copiado, não reescrito** — é para isso que serve o exemplo de
  [13-transactional-outbox.md §2.7](./13-transactional-outbox.md);
- **sem elipses** (`...`) dentro do corpo: se o arquivo é longo, escolha o método
  inteiro em vez de recortar;
- **sem `@Override`/imports** quando não agregam contexto, mas **com** as anotações
  (`@WithTransaction`, `@Scheduled`, `@Channel`) — elas são o argumento;
- comente **por que** o trecho importa logo abaixo, em prosa;
- se o trecho ilustrar um erro, marque com `🧩 DIDÁTICO — NÃO COPIAR` (ver
  [10-exemplos-contrarios-ao-dominio.md](./10-exemplos-contrarios-ao-dominio.md)).

Nomes de arquivo **em texto** (não como link) são permitidos e desejáveis: o leitor
sabe onde conferir, mas não depende do link para entender.

### 2.2 Configuração

```properties
# modulo/src/main/resources/application.properties
# Cap.X - <por que esta propriedade existe>
```

Toda propriedade nova precisa do comentário com o capítulo. É rastreabilidade.

### 2.3 Dependências

```xml
<!-- Cap.X - <por que este artefato> -->
<dependency>
    <groupId>io.quarkus</groupId>
    <artifactId>quarkus-alguma-coisa</artifactId>
    <scope>test</scope>  <!-- se for test -->
</dependency>
```

⚠️ **Regra 16 do [AGENTS.md](../../AGENTS.md):** verifique o `artifactId` contra o Quarkus
**3.39.3** antes de escrever. O livro usa nomes que já foram relocados (ex.:
`quarkus-junit5-mockito` → `quarkus-junit-mockito` no 3.31).

---

## 3. Divergências do livro

Esta é a seção **mais importante** do arquivo. Preencha mesmo que não haja divergência
(escreva "nenhuma"), porque a ausência de registro é indistinguível de esquecimento.

```markdown
| # | O que o livro faz | O que fazemos | Por quê | Onde está registrado |
|---|---|---|---|---|
| 1 | ... | ... | regra N do AGENTS.md / ADR X | `docs/adr/00X-*.md` |
```

Divergência **sem** regra ou ADR é dívida. Ou você documenta a razão, ou você volta para o
padrão.

---

## 4. Testes: camada por camada

| Camada | Teste criado | O que prova | Como roda |
|---|---|---|---|
| Domain | ... | invariante | `./mvnw -pl <mod> test -Dtest=...` |
| Application | ... | orquestração | idem |
| Adapter | ... | fronteira | idem |
| Integration | ... | infra real | idem |
| Native | ... | artefato | `./mvnw -pl <mod> verify -Pnative` |

Critérios do padrão (ver [04](./04-estrategia-de-testes-do-projeto.md)):

- [ ] nome `should<Comportamento>Quando<Condição>` quando a condição importar (preferência, não invariante)
- [ ] sem `io.quarkus`/`jakarta`/Mutiny em teste de domínio
- [ ] fake da porta em teste de aplicação
- [ ] `await()` proibido sob `@RunOnVertxContext`
- [ ] `RED` visível no histórico

---

## 5. Armadilhas

Toda dor vivida entra aqui **e** em [11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md).

```markdown
### 5.1 <Nome do erro>

- **Sintoma:** mensagem exata que aparece.
- **Causa:** por que acontece.
- **Correção:** o que mudou.
- **Prevenção:** regra que evita reincidência.
```

---

## 6. Conceitos que NÃO usamos (e por quê)

```markdown
| Conceito do capítulo | Por que não usamos | Quando reavaliar |
|---|---|---|
| ex.: entity == Panache | conflita com separação domínio/persistência | nunca (é regra) |
| ex.: virtual threads | CPU-bound é o pior caso; optamos por reatividade | se surgir caso I/O-bound imperativo |
```

Essa seção impede que alguém reintroduza a feature "porque o livro usa".

---

## 7. Checklist de estudo

- [ ] 5 perguntas que eu deveria saber responder depois de ler o capítulo.
- [ ] 3 perguntas que **não** sei responder (viram tarefa).

---

## 8. Referências

| Tipo | Referência |
|---|---|
| ADR | `docs/adr/00X-*.md` |
| Página do repositório | `docs/services.md`, `docs/contracts.md`, ... |
| Guia oficial Quarkus | URL da versão 3.39.3 |
| Fonte do artefato | `~/.m2/repository/...` (quando a doc oficial for vaga) |

---

## 9. Checklist de fecho

Antes de dar o capítulo por concluído:

- [ ] `docs/knowledge/README.md` com a linha do novo capítulo no índice e no mapa
- [ ] `docs/README.md` com a linha no "Mapa Livro → Documentação"
- [ ] `docs/roadmap.md` com o status e **evidência executável**
- [ ] ADRs criadas/atualizadas para toda divergência
- [ ] **todo conceito da tabela §2.1 tem o trecho de código embutido** (regra §2.1.1)
- [ ] nenhum link para arquivo de código em `src/`
- [ ] `docs/testing.md` atualizado se o padrão de teste mudou
- [ ] suíte do serviço verde: `./mvnw -pl <modulo> test`
- [ ] guardians executados

---

## 10. Checklist de governança do capítulo

> Copie este bloco para o arquivo final, preenchido.

```text
[ ] capítulo lido e anotado no momento da leitura
[ ] conceitos nomeados como no livro (para busca futura)
[ ] mapa capítulo → código → teste completo
[ ] codigo referenciado por trecho embutido, nao por link
[ ] divergências com regra/ADR (ou "nenhuma", explicitamente)
[ ] nada de API sem verificação contra 3.39.3
[ ] armadilhas reais registradas
[ ] conceitos recusados registrados com motivo
[ ] índice + roadmap + README da docs atualizados
[ ] suíte verde
[ ] guardians rodados
```

---

**Veja também:** [README.md](./README.md) ·
[04-estrategia-de-testes-do-projeto.md](./04-estrategia-de-testes-do-projeto.md) ·
[11-armadilhas-e-licoes.md](./11-armadilhas-e-licoes.md) ·
[10-exemplos-contrarios-ao-dominio.md](./10-exemplos-contrarios-ao-dominio.md)

---

_Última atualização: 2026-09-26 (template criado; §2.1.1 acrescenta a regra de citar
código por trecho embutido, e não por link)._
