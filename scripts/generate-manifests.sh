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
# cap.10 (item 11.3). O env KUBERNETES_NAMESPACE continua vindo da downward API.
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
    rg -v "app.quarkus.io/(quarkus-version|commit-id|vcs-uri|build-timestamp)" \
        "${module}/target/kubernetes/kubernetes.yml" > "${DEST}/${module}.yml"
done

echo "OK: manifests gerados em ${DEST}/ (imagens acme/*:${TAG})."