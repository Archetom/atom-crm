#!/bin/sh
set -eu

# Runs the private-adapter durability oracle. It accepts no PBX account, provider, telephone,
# recording, credential, endpoint or media input and cannot produce a real-PBX claim.
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

(cd "$root" && sh ./mvnw -q -pl infra/external -am test \
  -Dtest=B22PrivateAdapterDurabilityConformanceTest -Dsurefire.failIfNoSpecifiedTests=false)

created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
source_digest=$(shasum -a 256 \
  "$root/infra/external/src/test/java/io/github/archetom/crm/infra/external/call/B22PrivateAdapterDurabilityConformanceTest.java" \
  "$root/scripts/call-adapter-durability-check.sh" | shasum -a 256 | awk '{print $1}')
umask 077
tmp_report=$(mktemp "$report_dir/.call-adapter-report.XXXXXX")
printf '%s\n' "{\"contractVersion\":\"B22-PRIVATE-ADAPTER-DURABLE/1\",\"createdAt\":\"$created_at\",\"profile\":\"TEST_ONLY_DURABLE_ORACLE\",\"sourceDigest\":\"sha256:$source_digest\",\"checks\":[\"CP_FENCED_RECONNECT_AND_RESTART\",\"CP_TWO_LEG_PROOF_PARTITION\",\"PC_LEDGER_AMBIGUOUS_NO_REINVOKE\",\"PC_COMMAND_DIGEST_CONFLICT_CLOSED\",\"PD_CONNECTOR_PARTITIONED_AES_GCM_HMAC\",\"PD_DEADLINE_RETENTION_DELETE\",\"PM_FULL_AND_SINGLE_RANGE\",\"PM_CANCEL_INVALID_RANGE_AND_CONFIRMED_DELETE\"],\"pbxReal\":\"BLOCKED_RUNTIME_CONFORMANCE\",\"lineReal\":\"BLOCKED_EXTERNAL\",\"evidenceLevel\":\"DURABLE_SIMULATED\",\"productionReady\":false,\"blocked\":[\"REAL_ASTERISK_ADAPTER_AGAINST_PRIVATE_DURABLE_STATE\",\"BIDIRECTIONAL_TWO_LEG_MEDIA_PROOF\",\"REAL_PBX_SENSITIVE_SINK_LEAK_SCAN\",\"REAL_PBX_RECORDING_STORAGE_AND_DELETION\",\"AUTHORIZED_EXTERNAL_SIP_TRUNK\"]}" > "$tmp_report"
chmod 600 "$tmp_report"
mv -f "$tmp_report" "$report"
chmod 600 "$report"
echo "Call adapter durability report written (real PBX and external-line evidence remain blocked)."
