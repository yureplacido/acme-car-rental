#!/usr/bin/env bash
set -euo pipefail

# Cap.10 item 10 / cap.11 - gera e versiona os manifests Kubernetes de cada servico
# em others/k8s/<modulo>.yml (quarkus-kubernetes, profile kubernetes).
#
# Uso:
#   scripts/generate-manifests.sh            # tag imutavel 1.0.0 (default)
#   ACME_IMAGE_TAG=1.2.3 scripts/generate-manifests.sh
#
# As imagens embutidas sao acme/<modulo>:${ACME_IMAGE_TAG}. O default e a tag
# imutavel 1.0.0 (cap.11 item 11.4); a imagem correspondente deve existir (build
# local com quarkus-container-image-docker) e o digest fica em docs/deployment.md.
#
# O Deployment ja sai com QUARKUS_PROFILE=kubernetes (quarkus.kubernetes.env.vars,
# item 11.2) e com os probes liveness/readiness/startup apontando para o health do
# cap.10 (item 11.3). Como o quarkus-kubernetes 3.39.3 nao expoe config para
# terminationGracePeriodSeconds (default 10s hardcoded), o script fixa 45s para
# cobrir quarkus.shutdown.timeout (30s) + o drain final da outbox do billing (10s).
# O env KUBERNETES_NAMESPACE continua vindo da downward API.
# Decidir o namespace que o Stork consome, imagePullPolicy/registry e o deploy real
# ficam para o item 11.6. Nao roda testes (skipTests): a configuracao funcional da
# descoberta fica nos testes @Tag("kubernetes") (Maven profile kubernetes).
#
# Nota: o gerador nao ordena chaves/listas de forma estavel entre execucoes (HashMap),
# entao pequenas reordenacoes podem aparecer no diff; o conteudo e equivalente.

cd "$(dirname "$0")/.."

DEST="others/k8s"
TAG="${ACME_IMAGE_TAG:-1.0.0}"
mkdir -p "$DEST"

# inventory-service depende do contrato inventory-proto (schema-first).
./mvnw -q -pl inventory-proto install

for module in users-service reservation-service rental-service inventory-service billing-service; do
    echo "==> ${module}"
    ./mvnw -q -pl "${module}" package -P kubernetes -DskipTests \
        -Dquarkus.container-image.tag="${TAG}" \
        -Dquarkus.kubernetes.version="${TAG}"
    # Remove as anotacoes transitorias app.quarkus.io/* (commit-id, vcs-uri,
    # build-timestamp) para o arquivo versionado ficar estavel entre regeneracoes.
    # O quarkus-kubernetes 3.39.3 NAO expoe config para terminationGracePeriodSeconds (o default
    # 10s e' hardcoded em PodSpecLike). A janela do K8s precisa cobrir o graceful shutdown
    # (quarkus.shutdown.timeout=30s) + o drain final da outbox (10s), entao fixamos 45s.
    rg -v "app.quarkus.io/(quarkus-version|commit-id|vcs-uri|build-timestamp)" \
        "${module}/target/kubernetes/kubernetes.yml" \
        | sed -E 's/terminationGracePeriodSeconds: [0-9]+/terminationGracePeriodSeconds: 45/' \
        > "${DEST}/${module}.yml"
    # Guarda: se o default do quarkus-kubernetes mudar (ou a chave sumir), o sed
    # acima vira no-op silencioso e o pod passa a ser morto cedo demais. Falha o build.
    if ! grep -q 'terminationGracePeriodSeconds: 45' "${DEST}/${module}.yml"; then
        echo "ERRO: terminationGracePeriodSeconds: 45 ausente em ${DEST}/${module}.yml." >&2
        echo "      O quarkus-kubernetes pode ter mudado; revise a janela de graceful shutdown." >&2
        exit 1
    fi
done

echo "OK: manifests gerados em ${DEST}/ (imagens acme/*:${TAG})."