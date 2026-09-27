# ACME Car Rental — Documentação de Arquitetura

> **Fonte da verdade: o código.** Estes documentos são um **mapa vivo** da arquitetura.
> Se o código e o documento divergirem, o código vence — atualize este mapa.

Sistema de locação de veículos construído **capítulo a capítulo** seguindo o livro
*Quarkus in Action* (Štefanko & Martiška, Manning, 2025). Serviços pequenos e
independentes, cada um no seu próprio módulo Maven (mentalidade de microservices, sem reactor).

## Índice

| Página | O que cobre |
|---|---|
| [architecture.md](./architecture.md) | Visão geral: DDD, adapters, fluxos, protocolos e decisões |
| [services.md](./services.md) | Detalhe por serviço: portas, endpoints, dependências, repositório, estado |
| [contracts.md](./contracts.md) | Contratos (gRPC `inventory-proto`): versionamento e regras de compatibilidade |
| [deployment.md](./deployment.md) | Deploy local/Docker: `others/` (compose, Traefik, Swagger, Keycloak, env) |
| [testing.md](./testing.md) | Estratégia DDD/TDD: domínio, aplicação, adapters e integração |
| [domain.md](./domain.md) | Bounded Contexts, aggregates, value objects e evolução do domínio |
| [ddd-tdd-standards.md](./ddd-tdd-standards.md) | Padrão arquitetural obrigatório do repositório |
| [roadmap.md](./roadmap.md) | Evolução por capítulo e evidências executáveis |
| [knowledge/](./knowledge/README.md) | **Base de estudo** por capítulo do livro: conceito, padrão do projeto e lições |
| [knowledge/book-index/](./knowledge/book-index/README.md) | **Onde o livro trata cada assunto**, por capítulo e página (o texto do livro não está no repo) |

## Legenda de status

Usada em **todas** as páginas por componente/feature:

| Símbolo | Significado |
|---|---|
| ✅ | Implementado e validado |
| ⚠️ | Parcial / operacional, com pendência conhecida |
| 🚧 | Placeholder (estrutura/deps preparadas, sem implementação) |
| 🔜 | Planejado (capítulo ainda não lido/implementado) |

## Mapa Livro → Documentação

Use esta tabela para **saber onde registrar** cada aprendizado novo. Quando terminar um
capítulo, atualize as páginas indicadas e marque o item no [roadmap.md](./roadmap.md).

| Capítulo (livro) | Tema | Onde fica registrado | Base de estudo |
|---|---|---|---|
| 1 | O que é Quarkus | — (conceitual) | `knowledge/01-fundamentos-e-dev-mode.md` |
| 2 | Primeira aplicação | `services.md` | `knowledge/01-fundamentos-e-dev-mode.md` |
| 3 | Produtividade no dev | `services.md`, `testing.md` | `knowledge/01-fundamentos-e-dev-mode.md` |
| 4 | Communications (REST/GraphQL/gRPC) | `architecture.md`, `services.md`, `contracts.md` | `knowledge/02-comunicacoes.md` |
| 5 | Testing | `testing.md` | `knowledge/03-testes-quarkus.md`, `knowledge/04-estrategia-de-testes-do-projeto.md` |
| 6 | Exposing e securing web apps | `services.md` (users/OIDC), `deployment.md`, `roadmap.md` | `knowledge/05-web-oauth-e-modo-producao.md` |
| 7 | Database access | `services.md` (repositórios), `contracts.md`, `roadmap.md` | `knowledge/06-persistencia-transacoes-e-nosql.md` |
| 8 | Reactive programming | `architecture.md`, `services.md` | `knowledge/07-programacao-reativa.md` |
| 9 | Quarkus messaging | `services.md` (billing), `contracts.md`, `roadmap.md`, `adr/` | `knowledge/08-messaging-reativo.md`, `knowledge/09-padroes-de-resiliencia-em-messaging.md`, `knowledge/13-transactional-outbox.md` |
| 10 | Cloud-native patterns (health, metrics, tracing, FT, service discovery) | `roadmap.md`, `services.md` | `knowledge/book-index/cap10.txt` |
| 11 | Cloud, Kubernetes/OpenShift, serverless | `deployment.md`, `roadmap.md` | `knowledge/book-index/cap11.txt` |
| 12 | Custom Quarkus extensions | `roadmap.md` | `knowledge/book-index/cap12.txt` |

> ⚠️ **O livro é de outra versão.** *Quarkus in Action* usa **Quarkus 3.15.1 /
> MicroProfile 6.1**; este repositório está em **3.39.3**. Por isso o roadmap
> registra **capacidade + evidência**, e não o passo do livro — ver
> [roadmap.md](./roadmap.md) § "Como ler este roadmap".
>
> O texto do livro **não está neste repositório** (obra comercial). O sumário
> navegável por capítulo, com página, está em
> [knowledge/book-index/](./knowledge/book-index/README.md).

## Como trabalhar com OpenCode

O projeto possui agentes e comandos em `.opencode/` para revisar DDD, TDD, Quarkus e consistência arquitetural. `feature-implementer` é o agente padrão configurado em `opencode.json`.

Antes de uma mudança relevante, use `/preflight <feature>` e, após a mudança, `/ddd-audit`, `/tdd-audit`, `/quarkus-audit` ou `/architecture-audit` conforme o escopo.

## Duas famílias de documentação

| Família | Pergunta que responde | Onde |
|---|---|---|
| **Arquitetura** | "como o sistema é hoje?" | este índice, exceto `knowledge/` |
| **Estudo** (`knowledge/`) | "por que eu fiz assim, e o que eu learned?" | [`knowledge/`](./knowledge/README.md) |

`knowledge/` guarda o **raciocínio**: conceito do livro, divergência do padrão, armadilha
real, exemplo contrário ao domínio. Ele aponta para o código, mas **não** substitui as
páginas de arquitetura.

## Como manter

1. Cada página tem `Última atualização:` na primeira linha (data + capítulo do livro).
2. O código é a fonte da verdade — ao mudar código, sincronize o doc no mesmo PR/commit.
3. Todo componente novo nasce com um status (✅/⚠️/🚧/🔜).
4. Decisões novas entram em `architecture.md` → "Decisões (ADR-lite)".
5. Novo capítulo lido → preencher `knowledge/12-modelo-para-novos-capitulos.md` e adicionar
   a linha nos dois mapas acima.
6. Mudança no sumário do livro → regenerar `knowledge/book-index/` com
   `tools/book-index/extract.py` e commitar só o índice.

---

_Última atualização: 2026-09-26 (base `docs/knowledge/` para cap.1-9; cap.10-12 com escopo
conferido contra o livro; índice navegável do livro em `knowledge/book-index/`)._