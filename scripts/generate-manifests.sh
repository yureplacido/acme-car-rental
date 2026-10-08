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
# Nao roda testes (skipTests): a configuracao funcional da descoberta fica nos
# testes @Tag("kubernetes") (Maven profile kubernetes). O manifest nasce com o env
# KUBERNETES_NAMESPACE (downward API: o namespace do pod, consumido pelo cliente
# fabric8 quando k8s-namespace nao e setado), mas NAO ativa QUARKUS_PROFILE=kubernetes
# nem seta K8S_NAMESPACE - ativar o perfil de runtime no Deployment e decidir o
# namespace que o Stork consome sao itens do 11.6 (deploy real), junto com
# imagePullPolicy/registry (hoje as imagens vivem so local e o default e "Always").

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