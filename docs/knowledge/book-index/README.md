# Índice do livro — *Quarkus in Action*

> **O que isto é:** o sumário navegável do livro, por capítulo, com número de
> página. Serve para saber **onde** um assunto está, para abrir o trecho certo.
>
> **O que isto não é:** o texto do livro. Essa pasta não contém uma linha da
> obra. O texto integral fica em `book-text/`, que **não é versionado**.

## Por que o texto do livro não está no git

*Quarkus in Action* (Štefanko & Martiška, Manning, 2025) é obra comercial
protegida por direitos autorais. versionar o texto integral no repositório seria
redistribuir a obra — e o repositório é público no GitHub.

A regra é a mesma que já vale para `msg/` (fontes do SmallRye descompactadas de
um `.jar`, commit `a71411c`) e a mesma que
[12-modelo-para-novos-capitulos.md](../12-modelo-para-novos-capitulos.md) exige
para a base de estudo: **síntese, não transcrição**.

O que entra no git é a resposta para *"onde eu leio sobre X?"*. O que fica na
máquina é a obra.

## Divisão

Nomes idênticos nos dois diretórios: `cap10.txt` aqui e `book-text/cap10.txt` são
o mesmo capítulo.

| Arquivo | Páginas do PDF | Páginas impressas | Tema |
|---|---|---|---|
| `00-frontmatter.txt` | 1–26 | i–xxvi | prefácio, sobre o livro, autores |
| `part1.txt` | 27–28 | 1–2 | divisor |
| `cap01.txt` | 29–45 | 3–19 | What is Quarkus? |
| `cap02.txt` | 46–82 | 20–56 | Your first Quarkus application |
| `cap03.txt` | 83–106 | 57–80 | Enhancing developer productivity |
| `part2.txt` | 107–108 | 81–82 | divisor |
| `cap04.txt` | 109–154 | 83–128 | Handling communications |
| `cap05.txt` | 155–168 | 129–142 | Testing Quarkus applications |
| `cap06.txt` | 169–196 | 143–170 | Exposing and securing web applications |
| `cap07.txt` | 197–239 | 171–213 | Database access |
| `cap08.txt` | 240–255 | 214–229 | Reactive programming |
| `cap09.txt` | 256–296 | 230–270 | Quarkus messaging |
| `part3.txt` | 297–298 | 271–272 | divisor |
| `cap10.txt` | 299–329 | 273–303 | Cloud-native application patterns |
| `cap11.txt` | 330–377 | 304–351 | Quarkus applications in the cloud |
| `cap12.txt` | 378–394 | 352–368 | Custom Quarkus extensions |
| `appendixA.txt` | 395–398 | 369–372 | Gradle, Kotlin, outras linguagens |
| `appendixB.txt` | 399–402 | 373–376 | instalações de ferramentas |
| `appendixC.txt` | 403–404 | 377–378 | Quinoa e Renarde |
| `index.txt` | 405–417 | 379–391 | índice remissivo do livro |

Os divisores de parte (`part1/2/3.txt`) são arquivos próprios porque caem
entre dois capítulos e não pertencem a nenhum deles.

## O que tem em cada arquivo de índice

| Seção | Conteúdo |
|---|---|
| cabeçalho | páginas do PDF e impressas, aviso de versão, caminho do texto local |
| **O que este capítulo cobre** | o bloco `This chapter covers` do próprio livro |
| **Seções** | árvore completa de seções e subseções com página impressa |
| **Termos citados** | anotações, chaves de configuração e CamelCase, com contagem no capítulo |

Os termos citados são âncoras de busca: servem para decidir rápido se o
capítulo responde à pergunta antes de abrir o arquivo de 100 KB.

## Aviso de versão — leia antes de implementar

O livro é escrito contra **Quarkus 3.15.1 / MicroProfile 6.1**. Este repositório
está em **Quarkus 3.39.3** (`quarkus.platform.version`).

Consequência prática: **capítulo do livro não é tarefa do roadmap**. O roadmap
([roadmap.md](../../roadmap.md)) registra a **capacidade** que queremos, não o
passo do livro nem o nome do artefato que ele usou. A API concreta é decidida na
implementação e verificada contra 3.39.3 — regra 16 do [AGENTS.md](../../../AGENTS.md).

Onde o item do roadmap marca `[3.39.3]`, é porque o `artifactId`, a propriedade
ou o comportamento mudou entre a versão do livro e a nossa.

## Como regenerar

O texto integral exige o PDF, que fica fora do repositório:

```bash
tools/book-index/extract.py "/caminho/do/livro.pdf"
# ou
BOOK_PDF="/caminho/do/livro.pdf" tools/book-index/extract.py
```

O script refaz os dois lados: `book-text/` (integral, ignorado) e esta pasta
(índice, versionado). Depois é só commitar **esta pasta**.

Dependências: `pdftotext` e `mutool` (poppler-utils e mupdf-tools).

### O que o script faz com o texto

1. `pdftotext -layout` — modo de layout, para **preservar a indentação dos
   blocos de código** (o modo reflowed achata o Java).
2. Remove cabeçalhos corridos e folios. Neste livro o folio fica na **linha de
   cabeçalho**, não no rodapé: verso é `274  CHAPTER 10  Cloud-native…`, recto é
   `10.1  MicroProfile…  275`.
3. Desfaz hifenização de fim de linha. O PDF usa o mesmo glifo `-` para
   hifenização de sílaba (`experi-` + `ence`) e para hífen legítimo
   (`ready-` + `to-use`, `Open-` + `Telemetry`), então o script decide pelo
   **vocabulário do próprio capítulo**: se a cola já aparece escrita no capítulo,
   era hifenização e o hífen some; se os dois pedaços existem como palavras, o
   hífen era do termo e fica. Hífen seguido de dígito (`chapter-10`) sempre fica.
4. Normaliza espaços, remove linhas em branco excedentes e o glifo de bullet da
   fonte Symbol.

## Limitações conhecidas

- **Layout de duas colunas**: onde a próxima linha é a segunda coluna (sumário,
  citações de margem), o script **não** junta — juntar seria errado. Resultado:
  dois termos por arquivo ficam com hífen na ponta da linha.
- **Diagramas e figures** viram texto solto; não há descrição de imagem.
- **Front matter e índice** contêm números de página do livro, não do PDF.

---

**Veja também:** [README.md](../README.md) ·
[12-modelo-para-novos-capitulos.md](../12-modelo-para-novos-capitulos.md) ·
[roadmap.md](../../roadmap.md)

---

_Última criação: 2026-09-26 (índice gerado dos 12 capítulos, 3 partes, front
matter, 3 appendices e o índice remissivo)._
