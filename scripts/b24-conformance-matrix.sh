#!/bin/sh
# Executes B24 evidence lanes and writes one value-free matrix.  It deliberately keeps the
# runtime-compatible passedScenarios field limited to the real backend subset; no model or
# MySQL result can widen it into a production-readiness attestation.
set -eu

usage() {
  echo "usage: $0 --backend-report /absolute/path/to/backend.json --report /absolute/path/to/matrix.json" >&2
  exit 64
}

report=''
backend_report=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --report) report=${2:-}; shift 2 ;;
    --backend-report) backend_report=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$report" ] || usage
[ -n "$backend_report" ] || usage
case "$report" in /*) ;; *) usage ;; esac
case "$backend_report" in /*) ;; *) usage ;; esac
[ ! -e "$report" ] || { echo "refusing to replace existing report" >&2; exit 64; }
report_dir=$(dirname "$report")
[ -d "$report_dir" ] || { echo "report parent is unavailable" >&2; exit 73; }
[ -f "$backend_report" ] || { echo "backend report is unavailable" >&2; exit 73; }

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
# `b24-linux-unix-socket-canary.sh` runs the disposable Docker topology separately. This runner
# consumes that owner-only report instead of treating an in-process adapter self-attestation as
# backend evidence; deployment orchestration may not use tests as a substitute for that report.

(cd "$root" && sh ./mvnw -q -pl infra/external -am test \
  -Dtest=AttachmentStorageConformanceTest,AttachmentInspectionConformanceTest,SafeFileComposedFixtureTest \
  -Dsurefire.failIfNoSpecifiedTests=false)
(cd "$root" && CI=true sh ./mvnw -q -pl start -am test \
  -Dtest=AttachmentPersistenceIntegrationTest,B24aWorkTaskAttachmentIntegrationTest,B24bCrmAttachmentTargetIntegrationTest,B24cFileFieldIntegrationTest,AttachmentComposedConformanceTest \
  -Dsurefire.failIfNoSpecifiedTests=false)

backend_json=$(tr -d '\n' < "$backend_report")
prefix=$(printf '%s' "$backend_json" | sed 's/,"passedScenarios":\[[^]]*\]}$//')
passed=$(printf '%s' "$backend_json" | sed -n 's/^.*"passedScenarios":\(\[[^]]*\]\)}$/\1/p')
[ "$prefix" != "$backend_json" ] && [ -n "$passed" ] || {
  echo "backend report cannot be composed" >&2
  exit 70
}
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
source_digest=$(shasum -a 256 \
  "$root/scripts/b24-conformance-matrix.sh" \
  "$root/scripts/b24-linux-unix-socket-canary.sh" \
  "$root/infra/external/src/main/java/io/github/archetom/crm/infra/external/attachment/B24SecureLocalCanaryRunner.java" \
  "$root/infra/external/src/main/java/io/github/archetom/crm/infra/external/attachment/SecureLocalAttachmentStorage.java" \
  "$root/infra/external/src/main/java/io/github/archetom/crm/infra/external/attachment/ClamdInstreamStreamingInspectionEngine.java" \
  "$root/infra/external/src/test/java/io/github/archetom/crm/infra/external/attachment/AttachmentStorageConformanceTest.java" \
  "$root/infra/external/src/test/java/io/github/archetom/crm/infra/external/attachment/AttachmentInspectionConformanceTest.java" \
  "$root/infra/external/src/test/java/io/github/archetom/crm/infra/external/file/SafeFileComposedFixtureTest.java" \
  "$root/start/src/test/java/io/github/archetom/crm/AttachmentPersistenceIntegrationTest.java" \
  | shasum -a 256 | awk '{print $1}')

# The original fields before passedScenarios are intentionally preserved, so the independent
# runtime source can parse this report. Extra matrix fields are diagnostic-only and are never
# consulted by the readiness verifier.
tmp_report=$(mktemp "$report_dir/.b24-matrix.XXXXXX")
umask 077
printf '%s\n' "$prefix,\"passedScenarios\":$passed,\"matrixVersion\":\"B24-CONFORMANCE-MATRIX/1\",\"createdAt\":\"$created_at\",\"sourceDigest\":\"sha256:$source_digest\",\"evidenceLevel\":\"REAL_BACKEND_CONFORMANT\",\"productionReady\":false,\"layers\":[{\"name\":\"CONTRACT_CLOSED\",\"runner\":\"AttachmentStorageConformanceTest+AttachmentInspectionConformanceTest\",\"coverage\":[\"S-01\",\"S-04\",\"S-08\",\"S-10\",\"S-11\",\"I-01\",\"I-06\",\"I-07\",\"I-12\"]},{\"name\":\"DURABLE_SIMULATED\",\"runner\":\"B24-MySQL-Testcontainers\",\"coverage\":[\"B24A_PERSISTENCE\",\"B24B_CRM_TARGETS\",\"B24C_FILE_FIELDS\",\"COMPOSED_SYNTHETIC\"]},{\"name\":\"CONTRACT_CLOSED\",\"runner\":\"SafeFileComposedFixtureTest\",\"coverage\":[\"X-01\",\"X-02\",\"X-03\",\"X-04\",\"X-05\",\"X-06\",\"X-07\",\"X-08\"]},{\"name\":\"REAL_BACKEND_CONFORMANT\",\"runner\":\"Linux-secure-local+ClamAV-INSTREAM\",\"coverage\":$passed}],\"missingRealBackendScenarios\":[\"S-09\",\"S-10\",\"S-12\",\"S-13\",\"S-14\",\"I-03\",\"I-04\",\"I-05\",\"I-06\",\"I-08\",\"I-09\",\"I-10\",\"I-11\",\"I-14\",\"X-01\",\"X-02\",\"X-03\",\"X-04\",\"X-05\",\"X-06\",\"X-07\",\"X-08\"]}" > "$tmp_report"
chmod 600 "$tmp_report"
mv "$tmp_report" "$report"
chmod 600 "$report"
echo "B24 conformance matrix written; production readiness remains false."
