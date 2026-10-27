#!/bin/sh

set -eu

SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
REPOSITORY_ROOT=$(CDPATH= cd -- "${SCRIPT_DIR}/.." && pwd)
EXPECTED_VERSION=${EXPECTED_VERSION:-1.0.0}
ATOM_TEST_FORKS=${ATOM_TEST_FORKS:-1}

case "${ATOM_TEST_FORKS}" in
    1|2) ;;
    *) echo "ATOM_TEST_FORKS must be 1 or 2." >&2; exit 1 ;;
esac

cd "${REPOSITORY_ROOT}"

# Ordinary developer tests may skip Docker; a release cannot claim that coverage.
if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
    echo "Release verification requires a working Docker daemon for MySQL tests." >&2
    exit 1
fi

case "${EXPECTED_VERSION}" in
    *-SNAPSHOT)
        echo "Release version must not be a SNAPSHOT: ${EXPECTED_VERSION}" >&2
        exit 1
        ;;
esac

PROJECT_VERSION=$(
    sh ./mvnw --batch-mode --no-transfer-progress help:evaluate \
        -Dexpression=project.version \
        -DforceStdout \
        -q
)

if [ "${PROJECT_VERSION}" != "${EXPECTED_VERSION}" ]; then
    echo "Expected project version ${EXPECTED_VERSION}, found ${PROJECT_VERSION}" >&2
    exit 1
fi

SNAPSHOT_HITS=$(
    git grep -n -- '-SNAPSHOT' -- \
        ':(glob)**/pom.xml' \
        'conf/application.yml' \
        'README.md' \
        'README.en.md' \
        'llms.txt' \
        'start/src/test/java/io/github/archetom/crm/OpenApiIntegrationTest.java' \
        || true
)

if [ -n "${SNAPSHOT_HITS}" ]; then
    echo "Release metadata still contains SNAPSHOT versions:" >&2
    echo "${SNAPSHOT_HITS}" >&2
    exit 1
fi

for FILE in \
    README.md \
    README.en.md \
    llms.txt \
    conf/application.yml \
    start/src/test/java/io/github/archetom/crm/OpenApiIntegrationTest.java
do
    if ! grep -Fq "${EXPECTED_VERSION}" "${FILE}"; then
        echo "${FILE} does not declare release version ${EXPECTED_VERSION}" >&2
        exit 1
    fi
done

git diff --check
git diff --cached --check
if git rev-parse --verify HEAD^ >/dev/null 2>&1; then
    git diff --check HEAD^ HEAD
fi
CI=true sh ./mvnw --batch-mode --no-transfer-progress clean install \
    "-Datom.test.forks=${ATOM_TEST_FORKS}"
java scripts/VerifyDatabaseTests.java
