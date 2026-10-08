#!/usr/bin/env bash
set -euo pipefail

# Cap.10 item 10 - gera e versiona os manifests Kubernetes de cada servico em
# others/k8s/<modulo>.yml (quarkus-kubernetes, profile kubernetes). As imagens
# embutidas sao as do profile docker: acme/<modulo>:${project.version}
# (mesmo valor de ACME_IMAGE_TAG em others/.env).
#
# Uso:
#   scripts/generate-manifests.sh
#
# Nao roda testes (skipTests): a configuracao do runtime (%kubernetes, envs via
# K8S_NAMESPACE etc.) ja nasce nos manifests; a validacao funcional da descoberta
# fica nos testes @Tag("kubernetes") (Maven profile kubernetes).

cd "$(dirname "$0")/.."

DEST="others/k8s"
mkdir -p "$DEST"

# inventory-service depende do contrato inventory-proto (schema-first).
./mvnw -q -pl inventory-proto install

for module in users-service reservation-service rental-service inventory-service billing-service; do
    echo "==> ${module}"
    ./mvnw -q -pl "${module}" package -P kubernetes -DskipTests
    # Remove as anotacoes transitorias app.quarkus.io/* (commit-id, vcs-uri,
    # build-timestamp) para o arquivo versionado ficar estavel entre regeneracoes.
    rg -v "app.quarkus.io/(quarkus-version|commit-id|vcs-uri|build-timestamp)" \
        "${module}/target/kubernetes/kubernetes.yml" > "${DEST}/${module}.yml"
done

echo "OK: manifests gerados em ${DEST}/ (imagens acme/*:1.0.0-SNAPSHOT)."