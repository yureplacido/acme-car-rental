#!/usr/bin/env bash
set -euo pipefail

# Cap.10 item 10 - pre-build das imagens dos servicos consumidas pelo compose
# (image: + pull_policy: never). A tag e imutavel = ${project.version}
# (mesmo valor de ACME_IMAGE_TAG em others/.env).
#
# Uso:
#   scripts/build-images.sh            # imagens JVM (Maven profile docker)
#   scripts/build-images.sh native     # imagens NATIVAS (-P native,docker)
#
# Requer Docker daemon (quarkus-container-image-docker). No modo native, ou se
# GraalVM nao estiver instalada, use QUARKUS_NATIVE_CONTAINER_BUILD=true para
# compilar nativo dentro de um container (ex.: -P native,docker).

cd "$(dirname "$0")/.."

PROFILE="docker"
if [ "${1:-}" = "native" ]; then
    PROFILE="native,docker"
    if ! command -v native-image >/dev/null 2>&1 && [ -z "${GRAALVM_HOME:-}" ]; then
        export QUARKUS_NATIVE_CONTAINER_BUILD=true
        echo ":: GraalVM nao detectado; compilando nativo dentro de container (QUARKUS_NATIVE_CONTAINER_BUILD=true)."
    fi
fi

# inventory-service depende do contrato inventory-proto (schema-first); o install
# garante que o SNAPSHOT fique disponivel para o reactor do servico.
./mvnw -q -pl inventory-proto install

for module in users-service reservation-service rental-service inventory-service billing-service; do
    echo "==> ${module} (${PROFILE})"
    ./mvnw -q -pl "${module}" package -P "${PROFILE}"
done

echo "OK: imagens acme/* buildadas com o profile ${PROFILE}."