#!/usr/bin/env python3
"""Extrai o livro Quarkus in Action em texto puro, separado por capitulo.

Gera duas coisas:

1. ``book-text/`` -- texto integral, um arquivo por capitulo. NUNCA versionado
   (ver .gitignore): e obra comercial da Manning, e o repositorio versiona o
   indice, nao o livro.
2. ``docs/knowledge/book-index/`` -- indice por capitulo: sumario do proprio
   livro, arvore de secoes com numero de pagina, termos citados e ponteiro para
   o arquivo de texto local. Isso sim e versionado.

As fronteiras de pagina nao sao adivinhadas: vem do outline do PDF, resolving
os named destinations ate o objeto de pagina real.

Uso:
    tools/book-index/extract.py /caminho/do/livro.pdf
    BOOK_PDF=/caminho/do/livro.pdf tools/book-index/extract.py
"""

from __future__ import annotations

import argparse
import collections
import os
import re
import subprocess
import sys
import unicodedata
from datetime import date
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
TEXT_DIR = REPO / "book-text"
INDEX_DIR = REPO / "docs" / "knowledge" / "book-index"

BOOK_TITLE = "Quarkus in Action"
BOOK_AUTHORS = "Martin Štefanko, Jan Martiška"
BOOK_EDITION = "MANNING Publications, 2025"

# O livro e escrito contra Quarkus 3.15.1 / MicroProfile 6.1. O repositorio esta
# em 3.39.3 (quarkus.platform.version). Item de roadmap = capacidade; a API
# concreta e verificada contra 3.39.3 na implementacao (AGENTS.md regra 16).
BOOK_VERSION_NOTE = "Quarkus 3.15.1 / MicroProfile 6.1 (livro) x 3.39.3 (repositorio)"

# Front matter e em algarismos romanos; no corpo, pagina impressa = pdf - 26.
BODY_OFFSET = 26
ROMAN = [
    "i", "ii", "iii", "iv", "v", "vi", "vii", "viii", "ix", "x", "xi", "xii",
    "xiii", "xiv", "xv", "xvi", "xvii", "xviii", "xix", "xx", "xxi", "xxii",
    "xxiii", "xxiv", "xxv", "xxvi", "xxvii", "xxviii", "xxix", "xxx",
]

NOTICE = (
    "MATERIAL DE REFERENCIA - NAO VERSIONADO.\n"
    "Obra comercial protegida por direitos autorais. Este arquivo existe apenas\n"
    "na maquina local e e gerado por tools/book-index/extract.py.\n"
    "O que entra no repositorio e docs/knowledge/book-index/ (o indice).\n"
)


# --------------------------------------------------------------------------- #
# PDF: outline, paginas, texto
# --------------------------------------------------------------------------- #

class Pdf:
    def __init__(self, path: Path) -> None:
        self.path = path

    def _mutool(self, *args: str) -> str:
        proc = subprocess.run(
            ["mutool", "show", str(self.path), *args],
            capture_output=True, text=True, check=True,
        )
        return proc.stdout

    def page_count(self) -> int:
        return sum(1 for line in self._mutool("pages").splitlines()
                   if line.startswith("page "))

    def page_by_object(self) -> dict[str, int]:
        pages: dict[str, int] = {}
        for match in re.finditer(r"page (\d+) = (\d+) 0 R", self._mutool("pages")):
            pages[match.group(2)] = int(match.group(1))
        return pages

    def _named_destinations(self) -> dict[str, str]:
        """Resolve a arvore /Dests do PDF inteiro (recursiva, com visited set)."""
        names: dict[str, str] = {}
        seen: set[str] = set()

        def walk(ref: str) -> None:
            if ref in seen:
                return
            seen.add(ref)
            body = self._mutool(ref)
            for match in re.finditer(r"\(([^)]*)\)\s*(\d+)\s+0\s+R", body):
                names[match.group(1)] = match.group(2)
            kids = re.search(r"/Kids\s*\[(.*?)\]", body, re.S)
            if kids:
                for kid in re.findall(r"(\d+)\s+0\s+R", kids.group(1)):
                    walk(kid)

        trailer = self._mutool("trailer/Root/Names")
        dests = re.search(r"/Dests\s+(\d+)\s+0\s+R", trailer)
        if not dests:
            raise SystemExit("PDF sem /Names/Dests: nao da para ler o outline.")
        walk(dests.group(1))
        return names

    def outline(self) -> list[tuple[int, str, int | None]]:
        """Devolve (profundidade, titulo, pagina_pdf) de cada entrada do outline."""
        pages = self.page_by_object()
        names = self._named_destinations()
        entries: list[tuple[int, str, int | None]] = []
        for line in self._mutool("outline").splitlines():
            match = re.match(r'^[+|](\t*)"(.*?)"\s*#', line)
            if not match:
                continue
            depth = len(match.group(1)) + 1
            title = match.group(2)
            dest = re.search(r"#nameddest=(\S+)", line)
            page = None
            if dest:
                obj = names.get(dest.group(1))
                if obj:
                    ref = re.search(r"(\d+)\s+0\s+R", self._mutool(obj))
                    if ref:
                        page = pages.get(ref.group(1))
            entries.append((depth, title, page))
        return entries

    def text(self, first: int, last: int) -> str:
        """Texto com preservacao de layout (mantem a indentacao dos codigos)."""
        proc = subprocess.run(
            ["pdftotext", "-layout", "-f", str(first), "-l", str(last),
             str(self.path), "-"],
            capture_output=True, text=True, check=True,
        )
        return proc.stdout


# --------------------------------------------------------------------------- #
# Limpeza do texto
# --------------------------------------------------------------------------- #

# Glifo da fonte Symbol (area de uso privado U+F0A1) que o poppler extrai
# como bullet de lista.
PUA_MAP = {"": "•"}

# Cabecalho verso: "<folio>  CHAPTER 10  Cloud-native application patterns"
RE_HEADER_VERSO = re.compile(
    r"^\s*\d{1,3}\s+(?:CHAPTER\s+\d+|APPENDIX\s+[A-C]|INDEX)\b.*$")
# Cabecalho recto: "<secao 10.1>  MicroProfile, SmallRye, and Quarkus  <folio>"
RE_HEADER_RECTO = re.compile(
    r"^\s+\d{1,2}\.\d{1,2}(?:\.\d{1,2})?\s+\S.*?\s+\d{1,3}\s*$")
# Folio solto (so aparece no front matter, em algarismos romanos).
RE_FOLIO = re.compile(r"^\s*(?:\d{1,3}|[ivxlcdm]{1,7})\s*$")

# Quebra de linha por hifenizacao. O glifo '-' e o mesmo nos varios casos
# (ver dehyphenate), entao a decisao e feita com o vocabulario do capitulo.
RE_LINE_END_HYPHEN = re.compile(r"([A-Za-z][A-Za-z]*)-[ \t]*$")
RE_NEXT_WORD = re.compile(r"^[ \t]*([A-Za-z0-9][A-Za-z0-9]*)")


def printed_page(pdf_page: int) -> str:
    if pdf_page < 27:
        return ROMAN[pdf_page - 1] if pdf_page - 1 < len(ROMAN) else str(pdf_page)
    return str(pdf_page - BODY_OFFSET)


def _clean_page(page: str) -> list[str]:
    lines = page.splitlines()
    head = next((i for i, l in enumerate(lines) if l.strip()), None)
    if head is None:
        return []
    first = lines[head]
    if RE_HEADER_VERSO.match(first) or RE_HEADER_RECTO.match(first) or RE_FOLIO.match(first):
        lines[head] = ""
    tail = next((i for i in range(len(lines) - 1, -1, -1) if lines[i].strip()), None)
    if tail is not None and tail != head and RE_FOLIO.match(lines[tail]):
        lines[tail] = ""
    return lines


def vocabulary(text: str) -> set[str]:
    """Palavras do proprio capitulo, para decidir o que e hifenizacao de linha."""
    return set(re.findall(r"[A-Za-z]{2,}", text.lower()))


def dehyphenate(text: str) -> str:
    """Junta palavra quebrada no fim da linha.

    O PDF usa o mesmo glifo '-' para os dois casos, entao nao da para saber
    pelo texto se a linha termina em:

      - hifenizacao de silaba:  "experi-" + "ence"      -> experience
      - hifen legitimo do termo: "Open-" + "Source"     -> Open-Source
      - "ready-" + "to-use"                             -> ready-to-use

    Deletar o hifen sempre corrompe o segundo caso ("readyto-use"). A
    distincao usa o vocabulario do proprio capitulo:

      - a cola existe no texto  -> era hifenizacao, junta sem hifen
      - os dois pedacos existem -> era termo hifenizado, junta com hifen
      - nenhum dos dois         -> assume hifenizacao (o caso comum)

    Quando a proxima palavra comeca com digito, o hifen e do proprio termo
    ("chapter-10/docker-compose.yml") e e preservado.
    """
    vocab = vocabulary(text)
    exact = set(re.findall(r"[A-Za-z0-9][A-Za-z0-9-]*", text))
    lines = text.splitlines()
    out: list[str] = []
    position = 0
    while position < len(lines):
        line = lines[position]
        while position + 1 < len(lines):
            match = RE_LINE_END_HYPHEN.search(line)
            if not match:
                break
            following = lines[position + 1]
            token = RE_NEXT_WORD.match(following)
            if not token:
                break
            stem = match.group(1)
            word = token.group(1)
            rest = following[token.end():]
            if word[0].isdigit():
                merged = f"{stem}-{word}"      # chapter-10, secao-3
            elif f"{stem}{word}" in exact:
                merged = f"{stem}{word}"        # Open-Telemetry -> OpenTelemetry
            elif f"{stem}{word}".lower() in vocab:
                merged = f"{stem}{word}"        # hifenizacao de silaba
            elif stem.lower() in vocab and word.lower() in vocab:
                merged = f"{stem}-{word}"       # ready-to-use, Open-Source
            else:
                merged = f"{stem}{word}"        # caso comum: era hifenizacao
            line = line[:match.start()] + merged + rest
            position += 1
        out.append(line)
        position += 1
    return "\n".join(out)


def clean_text(raw: str) -> str:
    """Remove cabecalhos/folios, desfaz hifenizacao de fim de linha e normaliza espacos."""
    for src, dst in PUA_MAP.items():
        raw = raw.replace(src, dst)

    pages = raw.split("\f")
    out: list[str] = []
    for page in pages:
        out.extend(_clean_page(page))
    text = dehyphenate("\n".join(out))

    text = unicodedata.normalize("NFC", text)
    text = "\n".join(line.rstrip() for line in text.splitlines())
    text = re.sub(r"\n{3,}", "\n\n", text)
    return text.strip() + "\n"


def header(stem: str, title: str, first: int, last: int) -> str:
    return (
        f"CAPITULO/TITULO: {title}\n"
        f"Fonte: {BOOK_TITLE} ({BOOK_AUTHORS}, {BOOK_EDITION})\n"
        f"PDF: paginas {first}-{last} | impressas: "
        f"{printed_page(first)}-{printed_page(last)}\n"
        f"Versoes: {BOOK_VERSION_NOTE}\n"
        f"Gerado por: tools/book-index/extract.py em {date.today().isoformat()}\n"
        f"Indice correspondente: docs/knowledge/book-index/{stem}.txt\n"
        f"\n{NOTICE}\n"
        f"{'-' * 78}\n\n"
    )


# --------------------------------------------------------------------------- #
# Indice
# --------------------------------------------------------------------------- #

RE_TERM = re.compile(
    r"@[A-Z][A-Za-z0-9]+"                        # anotacoes
    r"|quarkus\.[a-z0-9]+(?:[.-][a-z0-9]+)+"     # chaves de configuracao
    r"|\b[A-Z][a-z0-9]+(?:[A-Z][a-z0-9]+)+\b"   # CamelCase
)
RE_BULLET = re.compile(r"^\s*[•\-]\s*(.+?)\s*$")
STOP_TERMS = {"Quarkus", "Java", "Maven", "GitHub", "OpenAPI", "Chapter"}


def key_terms(text: str, limit: int = 24) -> list[tuple[str, int]]:
    counts = collections.Counter(RE_TERM.findall(text))
    for junk in STOP_TERMS:
        counts.pop(junk, None)
    return counts.most_common(limit)


def chapter_covers(text: str) -> list[str]:
    """Bloco 'This chapter covers' da pagina de abertura do capitulo.

    As vezes o bullet quebra em duas linhas; a continuacao nao comeca com
    bullet, entao ela pertence ao item anterior.
    """
    lines = text.splitlines()
    for i, line in enumerate(lines):
        if line.strip().lower() != "this chapter covers":
            continue
        items: list[str] = []
        for follow in lines[i + 1:]:
            if not follow.strip():
                break
            bullet = RE_BULLET.match(follow)
            if bullet:
                items.append(bullet.group(1))
            elif items:
                items[-1] = f"{items[-1]} {follow.strip()}"
            else:
                items.append(follow.strip())
        if items:
            return items
    return []


def section_tree(entries, first: int, last: int) -> list[tuple[str, int]]:
    """Secoes (profundidade 4) e subsecoes (5) do capitulo, com pagina impressa."""
    rows: list[tuple[str, int]] = []
    for depth, title, page in entries:
        if depth not in (4, 5) or page is None:
            continue
        if not first <= page <= last:
            continue
        rows.append((title, page))
    rows.sort(key=lambda row: row[1])
    return rows


def render_index(
    stem: str,
    title: str,
    first: int,
    last: int,
    covers: list[str],
    sections: list[tuple[str, int]],
    terms: list[tuple[str, int]],
) -> str:
    out = [
        f"# {title}",
        "",
        f"> {BOOK_TITLE} ({BOOK_AUTHORS}, {BOOK_EDITION})",
        f"> PDF paginas {first}-{last} | impressas "
        f"{printed_page(first)}-{printed_page(last)}",
        f"> Versoes: {BOOK_VERSION_NOTE}",
        f"> Texto integral: `book-text/{stem}.txt` (local, fora do git)",
        "",
        "Indice gerado por `tools/book-index/extract.py`. Nao contem o texto do",
        "livro: contem onde o assunto esta, para saber qual arquivo abrir.",
        "",
    ]

    out.append("## O que este capitulo cobre")
    out.append("")
    if covers:
        out.extend(f"- {item}" for item in covers)
    else:
        out.append("- (o livro nao traz o bloco 'This chapter covers' aqui)")
    out.append("")

    out.append("## Secoes")
    out.append("")
    if sections:
        width = max(len(name) for name, _ in sections) + 3
        for name, page in sections:
            indent = "  " if re.match(r"^\d+\.\d+\.\d+", name) else ""
            out.append(f"{indent}{name.ljust(width)}p. {printed_page(page)}")
    else:
        out.append("(sem secoes no outline do PDF)")
    out.append("")

    out.append("## Termos citados (contagem no capitulo)")
    out.append("")
    if terms:
        for i in range(0, len(terms), 6):
            row = terms[i:i + 6]
            out.append("  " + "  ".join(f"`{name}` ({n})" for name, n in row))
    else:
        out.append("(nenhum)")
    out.append("")
    return "\n".join(out)


# --------------------------------------------------------------------------- #
# Divisao do livro
# --------------------------------------------------------------------------- #

def build_units(pdf: Pdf) -> list[tuple[str, str, int, int]]:
    """(stem, titulo, primeira_pagina, ultima_pagina) na ordem do livro.

    Profundidade do outline = numero de tabs + 1, ou seja: 2 = topo
    (Part/appendix/index), 3 = capitulo (1..12), 4 = secao (10.1),
    5 = subsecao (10.1.1).
    """
    entries = pdf.outline()
    total = pdf.page_count()

    chapters: dict[int, tuple[str, int]] = {}
    parts: dict[int, tuple[str, int]] = {}
    appendices: dict[str, tuple[str, int]] = {}
    index_entry: tuple[str, int] | None = None

    for depth, title, page in entries:
        if page is None:
            continue
        if depth == 2:
            found = re.match(r"^Part (\d)\b", title)
            if found:
                parts[int(found.group(1))] = (title, page)
                continue
            found = re.match(r"^appendix ([A-C])\b", title)
            if found:
                appendices[found.group(1)] = (title, page)
                continue
            if title.strip().lower() == "index":
                index_entry = (title, page)
        elif depth == 3:
            found = re.match(r"^(\d{1,2})\s+(.+?)$", title)
            if found:
                # O outline ja traz o numero no titulo; o stem tambem.
                chapters[int(found.group(1))] = (found.group(2), page)

    if len(chapters) != 12:
        raise SystemExit(
            f"esperava 12 capitulos no outline, achei {len(chapters)}: "
            f"{sorted(chapters)}")

    # Ordem de leitura: front matter, partes, capitulos, appendices, indice.
    # Cada parte e sua propria unidade porque o divisor cai entre dois capitulos
    # e nao pertence a nenhum deles.
    ordered: list[tuple[str, str, int]] = [
        ("00-frontmatter", "Front matter (algarismos romanos)", 1)
    ]
    for number in sorted(parts):
        title, page = parts[number]
        ordered.append((f"part{number}", title, page))
    for number in sorted(chapters):
        title, page = chapters[number]
        ordered.append((f"cap{number:02d}", f"Capitulo {number} - {title}", page))
    for letter in "ABC":
        if letter in appendices:
            title, page = appendices[letter]
            ordered.append((f"appendix{letter}", title, page))
    if index_entry:
        ordered.append(("index", "Index", index_entry[1]))

    # Cada unidade vai ate a pagina anterior ao inicio da proxima. Ordenar por
    # pagina e obrigatorio: as partes, os capitulos e os appendices sao
    # desesperados por categoria no outline, nao por ordem de leitura.
    ordered.sort(key=lambda unit: unit[2])
    units: list[tuple[str, str, int, int]] = []
    for position, (stem, title, first) in enumerate(ordered):
        last = ordered[position + 1][2] - 1 if position + 1 < len(ordered) else total
        units.append((stem, title, first, max(first, last)))
    return units


# --------------------------------------------------------------------------- #

def write(path: Path, content: str) -> None:
    path.write_text(content, encoding="utf-8")
    print(f"  {path.relative_to(REPO)}  ({len(content.encode('utf-8')):,} bytes)")


def main() -> int:
    ap = argparse.ArgumentParser(description=__doc__)
    ap.add_argument("pdf", nargs="?", default=os.environ.get("BOOK_PDF"),
                    help="caminho do PDF (ou use BOOK_PDF)")
    args = ap.parse_args()
    if not args.pdf:
        ap.error("informe o PDF como argumento ou via BOOK_PDF")

    pdf_path = Path(args.pdf).expanduser()
    if not pdf_path.is_file():
        raise SystemExit(f"PDF nao encontrado: {pdf_path}")

    pdf = Pdf(pdf_path)
    entries = pdf.outline()
    units = build_units(pdf)

    TEXT_DIR.mkdir(exist_ok=True)
    INDEX_DIR.mkdir(parents=True, exist_ok=True)

    print(f"{BOOK_TITLE} -> {len(units)} arquivos")
    for stem, title, first, last in units:
        text = clean_text(pdf.text(first, last))
        write(TEXT_DIR / f"{stem}.txt",
              header(stem, title, first, last) + text)

        index = render_index(
            stem, title, first, last,
            chapter_covers(text),
            section_tree(entries, first, last),
            key_terms(text),
        )
        write(INDEX_DIR / f"{stem}.txt", index)

    print(f"\nTexto integral em {TEXT_DIR.relative_to(REPO)}/ (fora do git)")
    print(f"Indice em {INDEX_DIR.relative_to(REPO)}/ (versionado)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
