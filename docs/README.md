# ACME Car Rental — Documentação de Arquitetura

> **Fonte da verdade: o código.** Estes documentos são um **mapa vivo** da arquitetura.
> Se o código e o documento divergirem, o código vence — atualize este mapa.

Sistema de locação de veículos construído **capítulo a capítulo** seguindo o livro
*Quarkus in Action* (Štefanko & Martiška, Manning, 2025). Serviços pequenos e
independentes, cada um no seu próprio módulo Maven (mentalidade de microservices, sem reactor).

## Índice

| Página | O que cobre |
|---|---|
| [architecture.md](./architecture.md) | Visão geral: diagrama de componentes, fluxos, protocolos, padrões e decisões |
| [services.md](./services.md) | Detalhe por serviço: portas, endpoints, dependências, repositório, estado |
| [contracts.md](./contracts.md) | Contratos (gRPC `inventory-proto`): versionamento e regras de compatibilidade |
| [deployment.md](./deployment.md) | Deploy local/Docker: docker-compose, Traefik (gateway), Swagger agregado, env |
| [testing.md](./testing.md) | Estratégia de testes (cap.5): classes, comandos, perfis, mocks |
| [roadmap.md](./roadmap.md) | Pendências organizadas por capítulo do livro |

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

| Capítulo (livro) | Tema | Onde fica registrado |
|---|---|---|
| 1 | O que é Quarkus | — (conceitual) |
| 2 | Primeira aplicação | `services.md` |
| 3 | Produtividade no dev | `services.md`, `testing.md` |
| 4 | Communications (REST/GraphQL/gRPC) | `architecture.md`, `services.md`, `contracts.md` |
| 5 | Testing | `testing.md` |
| 6 | Exposing e securing web apps | `services.md` (users/OIDC), `deployment.md`, `roadmap.md` |
| 7 | Database access | `services.md` (repositórios), `contracts.md`, `roadmap.md` |
| 8 | Reactive programming | `architecture.md`, `services.md` |
| 9 | Quarkus messaging | `services.md` (billing), `contracts.md`, `roadmap.md` |
| 10-12 | Cloud-native / cloud / extensões | `deployment.md`, `roadmap.md` |

## Como manter

1. Cada página tem `Última atualização:` na primeira linha (data + capítulo do livro).
2. O código é a fonte da verdade — ao mudar código, sincronize o doc no mesmo PR/commit.
3. Todo componente novo nasce com um status (✅/⚠️/🚧/🔜).
4. Decisões novas entram em `architecture.md` → "Decisões (ADR-lite)".

---

_Última atualização: 2026-09-19 (base cap.1-5 do livro)._