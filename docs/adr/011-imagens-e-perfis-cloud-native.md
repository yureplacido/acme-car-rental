# ADR 011 — Imagem pré-construída imutável + runtime por `QUARKUS_PROFILE` (item 10): a mesma imagem em Compose e Kubernetes

- **Status:** Accepted / implementada
- **Data:** 2026-10-08
- **Contexto:** Capítulo 10 — Cloud Native Patterns (configuração cloud-native, item 10); adianta o build de imagem e os manifests do cap. 11
- **Escopo:** build de imagem (Maven profile `docker`, tag imutável), consumo imutável no compose (`image:` + `pull_policy: never`), runtime por ambiente sem rebuild (`%docker` Consul / `%kubernetes` Stork-k8s / `%prod` jar no host), seleção de testes de discovery por backend e manifests Kubernetes versionados

## Contexto

O item 10 pede: a mesma imagem sobe em qualquer ambiente, comportamento stateless entre
instâncias. A casa é o Compose (plataforma de dev/operacional) com Consul como catálogo
(ADR 010), e o cap. 11 aponta para Kubernetes/OpenShift como produção. A decisão de item 10
é tornar o **artefato** independente do **ambiente**: um único build por serviço, com todo o
runtime decidido por configuração no launch (não no assembler).

Gate do repositório que pagou aqui: a regra 16 (novo comportamento de build/API conferido
contra a versão pinada `3.39.3`) e a regra 8 (perfis de build são infraestrutura — sem
convenção local de "imagem por serviço" inventada no pom).

## Restrições que moldam a decisão

- O `quarkus-container-image-docker` usa o `Dockerfile.jvm` do módulo (`src/main/docker/`);
  o base default segue **o JDK do build**, e o `Dockerfile.jvm` versionado pode divergir do
  JDK real da compilação (`maven-compiler` mira o JDK da máquina, sem `maven.compiler.release`
  fixo). Medido no smoke: reservation e inventory tinham `ubi9/openjdk-17-runtime` versionado
  enquanto as classes compilavam como Java 21 (major 65) contra runtime que só carrega até 61 —
  `UnsupportedClassVersionError` no boot. Os outros três já usavam 21 (baseline do projeto,
  `README`/`architecture.md`).
- O nome da **imagem** segue `quarkus.application.name` quando setado; para os publishers
  (`reservations`, `rentals` — ADR 010) isso misturaria identidade de catálogo com identidade
  de artefato. Fixado: `quarkus.container-image.name=${project.artifactId}`.
- O continue **tar/registry** depende de credencial de terceiro; a casa não tem registry.
  Tag imutável, imagens locais, `pull_policy: never`.
- O provider de discovery por plataforma (Stork `kubernetes` / `consul`) é escolha de runtime:
  o mesmo jar precisa resolver o catálogo certo sem rebuild (regra 3/4: a orquestração é do app,
  o discovery é infra nos adapters/config do app).

## Decisão

| Dimensão | Decisão | Onde |
|---|---|---|
| Build de imagem | Maven **profile `docker`** (`quarkus-container-image-docker`), imagem `acme/<artifactId>:<version>` | 5 poms (properties + profile) |
| Consumo | compose usa `image:` + `pull_policy: never`; **não** builda | `others/docker-compose.yml` |
| Build | `scripts/build-images.sh` (JVM `-P docker`; nativa `-P native,docker`, com `QUARKUS_NATIVE_CONTAINER_BUILD=true` se não há GraalVM) | `scripts/` |
| Runtime | `QUARKUS_PROFILE` decide: `%docker` (Consul), `%kubernetes` (Stork-k8s), `%prod` (jar no host) | `application.properties` dos módulos |
| Seleção de testes | `@Tag("consul")`/`@Tag("kubernetes")`; surefire exclui `kubernetes` por default; `-P kubernetes` troca para `consul` | 5 poms (`acme.test.discovery.excludedGroups`) |
| Manifests K8s | versionados em `others/k8s/<módulo>.yml` via `-P kubernetes package` (quarkus-kubernetes) | `scripts/generate-manifests.sh` + `others/k8s/` |

### 1. Perfil `docker` e tag imutável

Cada pom tem, em `<properties>` (não só no perfil — o quarkus-kubernetes embute a **mesma**
imagem nos manifests):

```text
quarkus.container-image.group=acme
quarkus.container-image.name=${project.artifactId}   # não herda quarkus.application.name
quarkus.container-image.tag=${project.version}        # ex.: 1.0.0-SNAPSHOT
```

E um profile:

```xml
<profile>
  <id>docker</id>
  <properties><quarkus.container-image.build>true</quarkus.container-image.build></properties>
</profile>
```

`scripts/build-images.sh` roda o loop `./mvnw -pl <módulo> package -P docker` (instala antes o
`inventory-proto` — o inventory-service compila contra o contrato schema-first). A variante
nativa adiciona `-P native` e seta `QUARKUS_NATIVE_CONTAINER_BUILD=true` para empacotar com
container de build (a máquina não tem GraalVM). `ACME_IMAGE_TAG=1.0.0-SNAPSHOT` vive em
`others/.env` e espelha `project.version` — uma única fonte por release.

### 2. Consumo imutável no compose

O compose parou de ter `build:`; cada serviço declara `image: acme/<módulo>:${ACME_IMAGE_TAG:-1.0.0-SNAPSHOT}`
com `pull_policy: never`. Trocar a tag no `.env` e re-oparar é o "deploy" local; rebuild passa a
ser decisão consciente (`build-images.sh`), não implícita do `up`.

### 3. Runtime por `QUARKUS_PROFILE` no mesmo artefato

O jar é **um só**; o ambiente é config no launch:

- `%docker` — catálogo **Consul** (`consul-host=consul` no compose), registro dos publishers
  via adapter próprio (ADR 010), `auth-server-url` do keycloak no container.
- `%kubernetes` — Stork **provider `kubernetes`** (`stork-service-discovery-kubernetes`):
  `k8s-namespace` obrigatório, `targetRef` obrigatório no EndpointAddress e
  `use-endpoint-slices=false` (medido na 3.39.3 — ver knowledge §1.8). Na reservation também
  desliga o registro Consul (`%kubernetes.acme.consul.registration.enabled=false`): em K8s quem
  publica é a plataforma (Service/probe), não o app. Dev services do kubernetes-client ficam off.
- `%prod` — jar no host (IntelliJ/CLI), localhost para tudo.

Exemplo na reservation:

```text
%docker.quarkus.stork.rentals.service-discovery.type=kubernetes   # não! é %kubernetes
%kubernetes.quarkus.stork.rentals.service-discovery.type=kubernetes
%kubernetes.quarkus.stork.rentals.service-discovery.k8s-namespace=${K8S_NAMESPACE:default}
%kubernetes.quarkus.stork.rentals.service-discovery.use-endpoint-slices=false
%kubernetes.acme.consul.registration.enabled=false
```

(Nota de redação: em `%kubernetes` **o provider de discovery é `kubernetes`**; o `%docker` nem
precisa setar o type, herda o default `consul` e só troca host. A chave `type` existe porque o
comportamento por ambiente é diferente de propósito — é isso que o item 10 pede.)

### 4. Seleção de testes de discovery por backend

Os testes de descoberta ganharam `@Tag("consul")` (resolução/registro contra Consul real de
testcontainers) e `@Tag("kubernetes")` (resolução contra mock do API server). O surefire de cada
pom exclui `kubernetes` por padrão (`acme.test.discovery.excludedGroups=kubernetes`); o profile
`-P kubernetes` vira `consul`. Assim o build CI roda o baseline (Consul) e `-P kubernetes`
valida o backend K8s com o mesmo classpath — inventário validado: baseline reward 45/
`-P kubernetes` 43, users 6/5 (o teste consul tem 2 métodos, o K8s 1), rental 10/8, inventory 45,
billing 62.

### 5. Manifests Kubernetes versionados

`quarkus-kubernetes` gera Deployment+Service+ServiceAccount+(RoleBinding `view`)+probes a partir
da config; `scripts/generate-manifests.sh` roda `package -P kubernetes -DskipTests` nos 5 módulos,
**remove as anotações transitórias `app.quarkus.io/*`** (commit-id/timestamp — manteriam diff em
toda regeneração) e versiona em `others/k8s/<módulo>.yml`. A imagem embutida é a **mesma** do
profile docker (por isso `container-image.name/tag` vivem nas properties). Probe de liveness/
readiness/startup mira `/q/health/{live,ready,started}`. Deploy real fica para os itens 11.4–11.6
do cap. 11 (imagem precisa ir a um registry ou `imagePullPolicy: IfNotPresent` num node local —
hoje o manifest herdou `Always`, default do quarkus-kubernetes).

## Alternativas consideradas

**Compose buildando (`build:`)** e cache de camada. Rejeitado no item 10: o build passou a ser
feito sob Maven (reproduzível, com `-P docker`), e o `pull_policy: never` torna o `up` o ato de
*consumir* exatamente o que foi buildado — sem "a imagem magicamente mudou" no meio do dia.

**`quarkus.container-image.name` derivado do `application.name`.** Rejeitado: mistura identidade
de imagem com identidade de catálogo (ADR 010); nos publishers a imagem variaria de nome sem
rebuild de nada (a property é de build). Imagem = `artifactId`, serviço = `application.name`.

**Registro etc. (Consul) para o backend K8s.** Rejeitado como paradigma: em Kubernetes a
plataforma publica via Service/endpoints; o app se registrar de novo seria dupla publicação e a
ADR 010 nem precisaria disso no cluster.

**Sub-rede default do compose (172.18.0.0/16).** Medido no smoke: colide com rota estática da
VPN do host, que sequestra o tráfego de/para containers (portas publicadas cegas). O compose
declara `networks.default.ipam.subnet=172.28.0.0/16`.

## Consequências

**Boas.**

- Item 10 fechado com smoke executável: `docker compose --profile all up -d` com as 5 imagens
  acme — 5 healths respondendo (users responde 302 de OIDC, esperado), catálogo Consul com
  `reservations`/`rentals` **passing** e realm do keycloak acessível. Ajustes do smoke com
  commit próprio (`e27c208`): aliases DNS explícitos (o compose v5 não registra aliases
  implícitos em rede com ipam custom — quebrava kafka/KRaft, keycloak, consul e os bancos),
  `billing-postgres` que o `%docker` do billing exigia e não existia, e os Dockerfiles JVM 17
  ultrapassados.
- Manifests versionados dão ao cap. 11 o "mesmo código, config declarativa" com diff rastreável.
- Testes de discovery continuam rápidos: baseline não paga o mock do API server, e o backend
  K8s roda sob demanda com `-P kubernetes`.

**Ruins / pendências.**

- Imagens **nativas** ainda não buildadas (máquina sem GraalVM; usa `QUARKUS_NATIVE_CONTAINER_BUILD=true`,
  build longo) — fica como evidência pendente do cap. 11.
- Os manifests apontam para `docker.io/acme/*:1.0.0-SNAPSHOT` com `imagePullPolicy: Always`;
  aplicar em cluster exige registry ou policy `IfNotPresent` — decidido no item de deploy (11.6).
- Re-registro do Consul continua sendo boot-once (ADR 010): o smoke pegou exatamente isso — ao
  recriar o Consul, o catálogo zerou até os apps reiniciarem. Aliases explícitos minimizam os
  reinícios de infra, mas o achado segue aberto para um item de saúde de catálogo.

## Evidência

| Mecanismo | O que prova |
|---|---|
| `scripts/build-images.sh` | 5 imagens `acme/<modulo>:1.0.0-SNAPSHOT` (JVM; validado) com base `ubi9/openjdk-21-runtime` |
| smoke `docker compose --profile all up -d` | imagens imutáveis sobem iguais em runtime `%docker`: healths 200 (users 302 OIDC), Consul `passing`, realm 200 |
| `@Tag("consul")`/`@Tag("kubernetes")` + `acme.test.discovery.excludedGroups` | baseline = Consul; `-P kubernetes` = backend K8s (mock do API server) — suítes verdes validadas nº `§4` |
| `others/k8s/*.yml` + `scripts/generate-manifests.sh` | Deployments/Services/probes com as mesmas imagens `acme/*` |
| `KubernetesRentalDiscoveryTest` / `ReservationsServiceDiscoveryKubernetesTest` | Stork-k8s resolve por `k8s-namespace`+`targetRef` com `use-endpoint-slices=false` (mock determinístico `client.resource(ep).create()`) |

## Relação com outras decisões

- **ADR 010** — o registro Consul segue sendo adapter próprio; em `%kubernetes` ele é desligado
  (a plataforma publica); o `kubernetes` da ADR 010 sobre provider Stork vale igual aqui.
- **ADR 002–007 (messaging)** — inalterado; o item 10 só altera como o app *sobe* (imagem), não
  o outbox/inbox.
- **cap. 11** — o build de imagem (11.2) e os manifests (11.3) estão adiantados; o deploy real
  (11.6) e o digest de imagem registrado continuam itens de lá.

## Estado da decisão

**Accepted / implementada**, com evidência executável no smoke do compose e nas suítes por
backend. Regra de bump (como ADR 009/010): a cada bump do Quarkus, re-validar (a) o base image
dos `Dockerfile.jvm` × JDK do build (sem `maven.compiler.release` fixo, o acoplamento é frouxo),
(b) o comportamento do compose com rede ipam custom (aliases explícitos) e (c) as chaves do
Stork-k8s (`k8s-namespace`, `targetRef`, `use-endpoint-slices`) e a geração dos manifests —
rodando baseline, `-P kubernetes` e o smoke do compose.