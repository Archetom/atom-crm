#!/bin/sh
set -eu

# Runs the core-side durable inbox oracle against disposable MySQL. It never accepts PBX,
# line, customer-number, recording, provider-payload or credential input.
usage() {
  echo "usage: $0 --report <absolute-report-path>" >&2
  exit 64
}

report=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --report) [ "$#" -ge 2 ] || usage; report=$2; shift 2 ;;
    *) usage ;;
  esac
done
[ -n "$report" ] || usage
case "$report" in /*) ;; *) usage ;; esac

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
report_dir=$(dirname -- "$report")
[ -d "$report_dir" ] || { echo "report parent is unavailable" >&2; exit 73; }

# The focused suite exercises the V82 migration, MySQL unique-key deduplication, concurrent
# replay, tenant/connector isolation and the connector registration lock contract.
(cd "$root" && CI=true sh ./mvnw -q -pl start -am test \
  -Dtest=ConnectorIntegrationTest -Dsurefire.failIfNoSpecifiedTests=false)

created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
source_digest=$(shasum -a 256 \
  "$root/infra/persistence/src/main/resources/db/migration/V82__create_call_runtime_reliability_foundation.sql" \
  "$root/application/src/main/java/io/github/archetom/crm/application/service/impl/CallFactInboxServiceImpl.java" \
  "$root/infra/persistence/src/main/java/io/github/archetom/crm/infra/persistence/repository/CallFactInboxRepositoryImpl.java" \
  "$root/start/src/test/java/io/github/archetom/crm/ConnectorIntegrationTest.java" | shasum -a 256 | awk '{print $1}')
umask 077
tmp_report=$(mktemp "$report_dir/.call-inbox-report.XXXXXX")
printf '%s\n' "{\"contractVersion\":\"B22-CORE-INBOX/1\",\"createdAt\":\"$created_at\",\"profile\":\"MYSQL_TESTCONTAINERS\",\"sourceDigest\":\"sha256:$source_digest\",\"checks\":[\"CONNECTOR_LOCK_BEFORE_WRITE\",\"EXACT_32_BYTE_OPAQUE_MATERIAL\",\"MYSQL_DELIVERY_DEDUPLICATION\",\"DUPLICATE_DIGEST_CONFLICT_CLOSED\",\"CONCURRENT_REPLAY\",\"TENANT_CONNECTOR_ISOLATION\"],\"pbxReal\":\"BLOCKED_RUNTIME_CONFORMANCE\",\"lineReal\":\"BLOCKED_EXTERNAL\",\"evidenceLevel\":\"DURABLE_SIMULATED\",\"productionReady\":false,\"blocked\":[\"PRIVATE_ADAPTER_JOURNAL_AND_FENCED_STASIS_OWNER\",\"TWO_LEG_MEDIA_PROOF\",\"AMBIGUOUS_RECONCILIATION\",\"SENSITIVE_MEDIA_RETENTION_AND_DELETION\",\"EXTERNAL_SIP_TRUNK\"]}" > "$tmp_report"
chmod 600 "$tmp_report"
mv -f "$tmp_report" "$report"
chmod 600 "$report"
echo "Call inbox conformance report written (PBX and external-line evidence remain blocked)."
