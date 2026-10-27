#!/bin/sh
set -eu

# Runs only against a disposable, isolated Asterisk/SIPp topology. It is deliberately not a
# production deployment tool and never accepts a trunk, customer number, or production secret.
usage() {
  echo "usage: $0 --allow-synthetic-sip-endpoints --report <absolute-report-path>" >&2
  exit 64
}

allow=false
report=""
while [ "$#" -gt 0 ]; do
  case "$1" in
    --allow-synthetic-sip-endpoints) allow=true; shift ;;
    --report) [ "$#" -ge 2 ] || usage; report=$2; shift 2 ;;
    *) usage ;;
  esac
done
[ "$allow" = true ] && [ -n "$report" ] || usage
case "$report" in /*) ;; *) usage ;; esac

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
resources="$root/infra/external/src/test/resources/b22/asterisk"
[ -d "$resources" ] || { echo "B22 Asterisk resources are unavailable" >&2; exit 70; }
report_dir=$(dirname -- "$report")
[ -d "$report_dir" ] || { echo "report parent is unavailable" >&2; exit 73; }

run_id="b22-canary-$$"
network="$run_id-net"
pbx="$run_id-pbx"
agent_a="$run_id-agent-a"
agent_b="$run_id-agent-b"
work=$(mktemp -d "${TMPDIR:-/private/tmp}/b22-pbx.XXXXXX")
secret=$(openssl rand -hex 24)
stasis_pid=""
asterisk_image="mlan/asterisk@sha256:c7b0b09903eec29f54601d32d7cd9aca859043144062db49e5054ae8f513745d"
sipp_image="sieteunoseis/sipp@sha256:9183ba7b21f49edcf028e30f9a086d5fa0fad3984caf9f15a05aea474de9bdd3"

cleanup() {
  [ -z "$stasis_pid" ] || kill "$stasis_pid" >/dev/null 2>&1 || true
  docker rm -f "$pbx" "$agent_a" "$agent_b" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -rf "$work"
}
trap cleanup EXIT INT TERM

cp -R "$resources"/. "$work"
# The literal is never committed, printed, persisted in the report, or reused after this process.
perl -0pi -e "s/\\$\\{B22_ARI_PASSWORD\\}/$secret/g" "$work/ari.conf"
perl -0pi -e "s/b22-sip-agent-a/$agent_a/g; s/b22-sip-agent-b/$agent_b/g" "$work/pjsip.conf"
docker network create "$network" >/dev/null
docker run -d --rm --name "$agent_a" --network "$network" "$sipp_image" \
  -sn uas -p 5060 -i 0.0.0.0 -rtp_echo >/dev/null
docker run -d --rm --name "$agent_b" --network "$network" "$sipp_image" \
  -sn uas -p 5060 -i 0.0.0.0 -rtp_echo >/dev/null

# Copy only missing image-owned seed files. The repository-owned test files remain the explicit
# ARI/PJSIP policy; the image's full seed avoids silently relying on a host Asterisk install.
docker run --rm -v "$work:/target" --entrypoint /bin/sh "$asterisk_image" -c \
  'for file in /usr/share/asterisk/config/*; do name=${file##*/}; [ -f "/target/$name" ] || cp -p "$file" "/target/$name"; done' >/dev/null
docker run -d --rm --name "$pbx" --network "$network" -p 127.0.0.1:18088:8088 \
  -v "$work:/srv/etc/asterisk" "$asterisk_image" >/dev/null

ready=false
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if curl --silent --show-error --fail --user "b22canary:$secret" \
      http://127.0.0.1:18088/ari/asterisk/info >/dev/null 2>&1; then ready=true; break; fi
  sleep 1
done
[ "$ready" = true ] || { echo "Asterisk ARI did not become ready" >&2; exit 75; }

# Exercise the actual ARI/Stasis data path without persisting any native event body. The JDK
# helper observes only the event type; the endpoint channel ID and its two safe helper markers
# remain in the cleaned temporary directory. This is intentionally not a durable stream owner.
probe_source="$root/scripts/B22StasisProbe.java"
[ -f "$probe_source" ] || { echo "B22 Stasis probe source is unavailable" >&2; exit 76; }
probe_output="$work/stasis-probe.out"
channel_a_reply="$work/stasis-channel-a.json"
channel_b_reply="$work/stasis-channel-b.json"
bridge_reply="$work/stasis-bridge.json"
bridge_status="$work/stasis-bridge-status.json"
cp "$probe_source" "$work/B22StasisProbe.java"
javac -d "$work" "$work/B22StasisProbe.java"
B22_ARI_PASSWORD="$secret" java -cp "$work" B22StasisProbe \
  'ws://127.0.0.1:18088/ari/events?app=b22-canary&subscribeAll=false' > "$probe_output" 2>&1 &
stasis_pid=$!
probe_ready=false
for _ in 1 2 3 4 5; do
  if grep -q '^READY$' "$probe_output"; then probe_ready=true; break; fi
  sleep 1
done
[ "$probe_ready" = true ] || { echo "Asterisk Stasis WebSocket did not become ready" >&2; exit 77; }
kill -0 "$stasis_pid" >/dev/null 2>&1 || { echo "Asterisk Stasis WebSocket did not remain open" >&2; exit 78; }
curl --silent --show-error --fail --user "b22canary:$secret" --request POST \
  --data '' 'http://127.0.0.1:18088/ari/channels?endpoint=PJSIP%2Fagent-a&app=b22-canary' \
  > "$channel_a_reply"
curl --silent --show-error --fail --user "b22canary:$secret" --request POST \
  --data '' 'http://127.0.0.1:18088/ari/channels?endpoint=PJSIP%2Fagent-b&app=b22-canary' \
  > "$channel_b_reply"
channel_a=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$channel_a_reply" | head -n 1)
channel_b=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$channel_b_reply" | head -n 1)
[ -n "$channel_a" ] && [ -n "$channel_b" ] \
  || { echo "ARI originate did not return both channel identities" >&2; exit 79; }
legs_ready=false
for _ in 1 2 3 4 5 6 7 8 9 10; do
  if grep -q '^TWO_LEGS_READY$' "$probe_output"; then legs_ready=true; break; fi
  sleep 1
done
[ "$legs_ready" = true ] || { echo "Asterisk did not emit both StasisStart events" >&2; exit 80; }
curl --silent --show-error --fail --user "b22canary:$secret" --request POST --data '' \
  'http://127.0.0.1:18088/ari/bridges?type=mixing' > "$bridge_reply"
bridge_id=$(sed -n 's/.*"id":"\([^"]*\)".*/\1/p' "$bridge_reply" | head -n 1)
[ -n "$bridge_id" ] || { echo "ARI bridge creation did not return an identity" >&2; exit 81; }
curl --silent --show-error --fail --user "b22canary:$secret" --request POST --data '' \
  "http://127.0.0.1:18088/ari/bridges/$bridge_id/addChannel?channel=$channel_a%2C$channel_b" >/dev/null
if ! wait "$stasis_pid"; then
  echo "Asterisk did not emit the expected bridge-entry events" >&2
  exit 82
fi
stasis_pid=""
curl --silent --show-error --fail --user "b22canary:$secret" \
  "http://127.0.0.1:18088/ari/bridges/$bridge_id" > "$bridge_status"
grep -F -q "\"$channel_a\"" "$bridge_status" \
  && grep -F -q "\"$channel_b\"" "$bridge_status" \
  || { echo "ARI bridge did not retain both originated channels" >&2; exit 83; }
curl --silent --show-error --fail --user "b22canary:$secret" --request DELETE \
  "http://127.0.0.1:18088/ari/bridges/$bridge_id" >/dev/null 2>&1 || true
curl --silent --show-error --fail --user "b22canary:$secret" --request DELETE \
  "http://127.0.0.1:18088/ari/channels/$channel_a" >/dev/null 2>&1 || true
curl --silent --show-error --fail --user "b22canary:$secret" --request DELETE \
  "http://127.0.0.1:18088/ari/channels/$channel_b" >/dev/null 2>&1 || true

# This runner intentionally records the transport boundary separately. The later private adapter
# must add durable journal/ledger, fenced Stasis WebSocket ownership, two-leg media proof,
# recording range/cancel/delete and the CP/PC/PD/PM oracle before it may claim PBX_REAL=PASSED.
created_at=$(date -u +%Y-%m-%dT%H:%M:%SZ)
source_digest=$(shasum -a 256 "$root/scripts/b22-pbx-canary.sh" \
  "$root/scripts/B22StasisProbe.java" "$resources"/*.conf | shasum -a 256 | awk '{print $1}')
tmp_report=$(mktemp "$report_dir/.b22-pbx-report.XXXXXX")
umask 077
printf '%s\n' "{\"contractVersion\":\"B22-PBX-CANARY/1\",\"createdAt\":\"$created_at\",\"profile\":\"ASTERISK_ARI_STASIS_SYNTHETIC_SIP\",\"sourceDigest\":\"sha256:$source_digest\",\"asteriskImageDigest\":\"sha256:c7b0b09903eec29f54601d32d7cd9aca859043144062db49e5054ae8f513745d\",\"sippImageDigest\":\"sha256:9183ba7b21f49edcf028e30f9a086d5fa0fad3984caf9f15a05aea474de9bdd3\",\"transportChecks\":[\"ARI_HTTP_AUTHENTICATED\",\"ARI_STASIS_WEBSOCKET_TWO_START_EVENTS\",\"ARI_ORIGINATE_TWO_SYNTHETIC_SIP_ENDPOINTS\",\"ARI_MIXING_BRIDGE_MEMBERSHIP\",\"ARI_BRIDGE_ENTRY_EVENTS\",\"SIPP_SOFTWARE_ENDPOINTS_STARTED\"],\"pbxReal\":\"BLOCKED_RUNTIME_CONFORMANCE\",\"lineReal\":\"BLOCKED_EXTERNAL\",\"blocked\":[\"FENCED_STASIS_EVENT_OWNER\",\"DURABLE_JOURNAL_AND_LEDGER\",\"TWO_LEG_MEDIA_PROOF\",\"AMBIGUOUS_RECONCILIATION\",\"SENSITIVE_DATA_AND_RECORDING_RANGE\",\"RETENTION_AND_CONFIRMED_DELETION\",\"EXTERNAL_SIP_TRUNK\"],\"evidenceLevel\":\"CONTRACT_CLOSED\"}" > "$tmp_report"
chmod 600 "$tmp_report"
mv -f "$tmp_report" "$report"
chmod 600 "$report"
echo "B22 PBX transport canary report written (runtime conformance remains blocked)."
