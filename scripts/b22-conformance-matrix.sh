#!/bin/sh
# Composes the separately executed B22 evidence lanes.  It never accepts an endpoint, line,
# connector, customer, credential, recording, provider account or native PBX value, and cannot
# promote the private oracle or synthetic SIP transport into a real-PBX/real-line assertion.
set -eu

usage() {
  echo "usage: $0 --private-report /absolute/path --core-report /absolute/path --pbx-report /absolute/path --report /absolute/path" >&2
  exit 64
}

private_report=''
core_report=''
pbx_report=''
report=''
while [ "$#" -gt 0 ]; do
  case "$1" in
    --private-report) private_report=${2:-}; shift 2 ;;
    --core-report) core_report=${2:-}; shift 2 ;;
    --pbx-report) pbx_report=${2:-}; shift 2 ;;
    --report) report=${2:-}; shift 2 ;;
    *) usage ;;
  esac
done

for value in "$private_report" "$core_report" "$pbx_report" "$report"; do
  [ -n "$value" ] || usage
  case "$value" in /*) ;; *) usage ;; esac
done
[ ! -e "$report" ] || { echo "refusing to replace existing report" >&2; exit 64; }
[ -d "$(dirname "$report")" ] || { echo "report parent is unavailable" >&2; exit 73; }

require_owner_only() {
  [ -f "$1" ] || { echo "input report is unavailable" >&2; exit 73; }
  mode=$(stat -f '%Lp' "$1" 2>/dev/null || stat -c '%a' "$1")
  [ "$mode" = 600 ] || { echo "input report is not owner-only" >&2; exit 70; }
}
for value in "$private_report" "$core_report" "$pbx_report"; do require_owner_only "$value"; done

require_json() {
  jq -e "$2" "$1" >/dev/null 2>&1 || { echo "input report is not the required B22 evidence lane" >&2; exit 70; }
}
require_json "$private_report" '.contractVersion == "B22-PRIVATE-ADAPTER-DURABLE/1"
  and .profile == "TEST_ONLY_DURABLE_ORACLE" and .evidenceLevel == "DURABLE_SIMULATED"
  and .pbxReal == "BLOCKED_RUNTIME_CONFORMANCE" and .lineReal == "BLOCKED_EXTERNAL"
  and .productionReady == false'
require_json "$core_report" '.contractVersion == "B22-CORE-INBOX/1"
  and .profile == "MYSQL_TESTCONTAINERS" and .evidenceLevel == "DURABLE_SIMULATED"
  and .pbxReal == "BLOCKED_RUNTIME_CONFORMANCE" and .lineReal == "BLOCKED_EXTERNAL"
  and .productionReady == false'
require_json "$pbx_report" '.contractVersion == "B22-PBX-CANARY/1"
  and .profile == "ASTERISK_ARI_STASIS_SYNTHETIC_SIP" and .evidenceLevel == "CONTRACT_CLOSED"
  and .pbxReal == "BLOCKED_RUNTIME_CONFORMANCE" and .lineReal == "BLOCKED_EXTERNAL"
  and (.transportChecks | type == "array")'

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
source_digest=$(shasum -a 256 "$root/scripts/b22-conformance-matrix.sh" | awk '{print $1}')
private_digest=$(shasum -a 256 "$private_report" | awk '{print $1}')
core_digest=$(shasum -a 256 "$core_report" | awk '{print $1}')
pbx_digest=$(shasum -a 256 "$pbx_report" | awk '{print $1}')

tmp_report=$(mktemp "$(dirname "$report")/.b22-matrix.XXXXXX")
umask 077
printf '%s\n' "{\"contractVersion\":\"B22-EVIDENCE-MATRIX/1\",\"createdAt\":\"$created_at\",\"sourceDigest\":\"sha256:$source_digest\",\"inputReportDigests\":[\"sha256:$private_digest\",\"sha256:$core_digest\",\"sha256:$pbx_digest\"],\"evidenceLevel\":\"DURABLE_SIMULATED\",\"productionReady\":false,\"pbxReal\":\"BLOCKED_RUNTIME_CONFORMANCE\",\"lineReal\":\"BLOCKED_EXTERNAL\",\"layers\":[{\"name\":\"CONTRACT_CLOSED\",\"runner\":\"Asterisk-ARI-Stasis-SIPp\",\"coverage\":[\"ARI_HTTP_AUTHENTICATED\",\"ARI_STASIS_WEBSOCKET_TWO_START_EVENTS\",\"ARI_ORIGINATE_TWO_SYNTHETIC_SIP_ENDPOINTS\",\"ARI_MIXING_BRIDGE_MEMBERSHIP\",\"ARI_BRIDGE_ENTRY_EVENTS\"]},{\"name\":\"DURABLE_SIMULATED\",\"runner\":\"B22PrivateAdapterDurabilityConformanceTest\",\"coverage\":[\"CP_FENCED_RECONNECT_AND_RESTART\",\"CP_TWO_LEG_PROOF_PARTITION\",\"PC_LEDGER_AMBIGUOUS_NO_REINVOKE\",\"PD_CONNECTOR_PARTITIONED_AES_GCM_HMAC\",\"PM_FULL_AND_SINGLE_RANGE\"]},{\"name\":\"DURABLE_SIMULATED\",\"runner\":\"B22-Core-Inbox-MySQL-Testcontainers\",\"coverage\":[\"CONNECTOR_LOCK_BEFORE_WRITE\",\"MYSQL_DELIVERY_DEDUPLICATION\",\"CONCURRENT_REPLAY\",\"TENANT_CONNECTOR_ISOLATION\"]}],\"blocked\":[\"PRIVATE_ASTERISK_ADAPTER_DURABLE_JOURNAL_LEDGER_AND_FENCED_STREAM_OWNER\",\"EVENT_DERIVED_BIDIRECTIONAL_TWO_LEG_MEDIA_PROOF\",\"REAL_PBX_AMBIGUOUS_RECONCILIATION\",\"REAL_PBX_SENSITIVE_SINK_AUDIT_RECORDING_RANGE_RETENTION_AND_CONFIRMED_DELETION\",\"AUTHORIZED_EXTERNAL_SIP_TRUNK_OR_CLOUD_TELEPHONY_TEST_ACCOUNT\"]}" > "$tmp_report"
chmod 600 "$tmp_report"
mv "$tmp_report" "$report"
chmod 600 "$report"
echo "B22 evidence matrix written; PBX and external-line readiness remain blocked."
