#!/bin/sh
# Runs only synthetic B24 files through a disposable Linux/ClamAV Unix-socket topology.
# It is a partial backend canary: a successful report cannot make runtime or production ready.
set -eu

usage() {
  echo "usage: $0 --report /absolute/path/to/report.json [--skip-build]" >&2
  exit 64
}

report=''
skip_build=false
while [ "$#" -gt 0 ]; do
  case "$1" in
    --report) report=${2:-}; shift 2 ;;
    --skip-build) skip_build=true; shift ;;
    *) usage ;;
  esac
done

[ -n "$report" ] || usage
case "$report" in /*) ;; *) echo "--report must be absolute" >&2; exit 64 ;; esac
[ ! -e "$report" ] || { echo "refusing to replace existing report" >&2; exit 64; }
[ -d "$(dirname "$report")" ] || { echo "report parent must exist" >&2; exit 64; }

repo=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

if [ "$skip_build" = false ]; then
  (cd "$repo" && sh ./mvnw -q -pl infra/external -am install -DskipTests)
  (cd "$repo" && sh ./mvnw -q -pl infra/external dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/b24-linux-deps)
fi

for directory in domain application api infra/persistence infra/facade infra/rest start shared infra/external; do
  [ -d "$repo/$directory/target/classes" ] || {
    echo "missing runtime classes for $directory; omit --skip-build" >&2
    exit 1
  }
done
[ -d "$repo/infra/external/target/b24-linux-deps" ] || {
  echo "missing runtime dependencies; omit --skip-build" >&2
  exit 1
}

run_id="b24-linux-canary-$$"
network="$run_id-net"
socket_volume="$run_id-socket"
database_volume="$run_id-db"
storage_volume="$run_id-storage"
report_volume="$run_id-report"
initial_clamd="$run_id-clamd-init"
isolated_clamd="$run_id-clamd"
runner="$run_id-runner"
clamav_image="clamav/clamav@sha256:d54ef59d5d07ff243c669789f51e0c3d9dfa9945f62b515be84febe8e1db0dbf"
runner_image="eclipse-temurin@sha256:201fbb8886b2d273218aa3a192f0afbf7b5ff65ee8cc6ef47f5dce2171f013ea"

cleanup() {
  docker rm -f "$runner" "$isolated_clamd" "$initial_clamd" >/dev/null 2>&1 || true
  docker volume rm "$socket_volume" "$database_volume" "$storage_volume" "$report_volume" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
}
trap cleanup EXIT HUP INT TERM

docker network create --internal "$network" >/dev/null
for volume in "$socket_volume" "$database_volume" "$storage_volume" "$report_volume"; do
  docker volume create "$volume" >/dev/null
done

# Definition download happens before isolation. Only the initialized database volume crosses
# that boundary; the scanner itself gets no external network and the Java runner gets none.
docker run -d --name "$initial_clamd" \
  -v "$socket_volume:/tmp" -v "$database_volume:/var/lib/clamav" \
  "$clamav_image" >/dev/null
for attempt in $(seq 1 180); do
  if docker exec "$initial_clamd" sh -c 'test -S /tmp/clamd.sock'; then break; fi
  sleep 1
  if [ "$attempt" -eq 180 ]; then
    echo "ClamAV definitions/socket did not become ready" >&2
    exit 1
  fi
done
docker rm -f "$initial_clamd" >/dev/null

docker run -d --name "$isolated_clamd" --network "$network" \
  -v "$socket_volume:/tmp" -v "$database_volume:/var/lib/clamav" \
  "$clamav_image" >/dev/null
for attempt in $(seq 1 45); do
  if docker exec "$isolated_clamd" sh -c 'test -S /tmp/clamd.sock'; then break; fi
  sleep 1
  if [ "$attempt" -eq 45 ]; then
    echo "isolated ClamAV Unix socket did not become ready" >&2
    exit 1
  fi
done

runtime_classpath='/workspace/domain/target/classes:/workspace/application/target/classes:/workspace/api/target/classes:/workspace/infra/persistence/target/classes:/workspace/infra/facade/target/classes:/workspace/infra/rest/target/classes:/workspace/start/target/classes:/workspace/shared/target/classes:/workspace/infra/external/target/classes:/workspace/infra/external/target/b24-linux-deps/*'
docker create --name "$runner" --network none \
  -v "$repo:/workspace:ro" -v "$socket_volume:/tmp:ro" \
  -v "$storage_volume:/work/root" -v "$report_volume:/work/reports" \
  --entrypoint /bin/sh "$runner_image" -lc \
  "umask 077; mkdir -p /work/root /work/reports; chmod 700 /work/root /work/reports; exec java -cp '$runtime_classpath' io.github.archetom.crm.infra.external.attachment.B24SecureLocalCanaryRunner --allow-synthetic-canary --root /work/root --report /work/reports/report.json --binding-base64 AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA --policy-digest b24-linux-canary-policy-v1 --detector-catalog-revision bounded-magic-v1 --allowlist-revision pdf-png-jpeg-v1 --clamd-unix-socket /tmp/clamd.sock" >/dev/null
docker start -a "$runner"

docker run --rm --network none -v "$report_volume:/work/reports:ro" --entrypoint /bin/sh "$runner_image" -lc \
  'test "$(stat -c %a /work/reports)" = 700 && test "$(stat -c %a /work/reports/report.json)" = 600' >/dev/null
docker cp "$runner:/work/reports/report.json" "$report"
chmod 600 "$report"
echo "B24 Linux Unix-socket partial backend report written: $report"
