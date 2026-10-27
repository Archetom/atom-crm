#!/bin/sh
# Executes one explicit, value-free public GLEIF canary. It cannot enable source readiness.
set -eu

usage() {
  echo "usage: $0 --allow-live-canary --report /absolute/path/to/report.json [--skip-build]" >&2
  exit 64
}

allow=false
report=''
skip_build=false
while [ "$#" -gt 0 ]; do
  case "$1" in
    --allow-live-canary) allow=true; shift ;;
    --report) report=${2:-}; shift 2 ;;
    --skip-build) skip_build=true; shift ;;
    *) usage ;;
  esac
done
[ "$allow" = true ] && [ -n "$report" ] || usage
case "$report" in /*) ;; *) usage ;; esac
[ ! -e "$report" ] || { echo "refusing to replace existing report" >&2; exit 64; }
[ -d "$(dirname "$report")" ] || { echo "report parent is unavailable" >&2; exit 73; }

root=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
if [ "$skip_build" = false ]; then
  (cd "$root" && sh ./mvnw -q -pl infra/external -am install -DskipTests)
  (cd "$root" && sh ./mvnw -q -pl infra/external dependency:copy-dependencies \
    -DincludeScope=runtime -DoutputDirectory=target/b23-gleif-deps)
fi

for directory in domain application api infra/persistence infra/facade infra/rest start shared infra/external; do
  [ -d "$root/$directory/target/classes" ] || {
    echo "missing runtime classes for $directory; omit --skip-build" >&2
    exit 1
  }
done
[ -d "$root/infra/external/target/b23-gleif-deps" ] || {
  echo "missing runtime dependencies; omit --skip-build" >&2
  exit 1
}

runtime_classpath="$root/domain/target/classes:$root/application/target/classes:$root/api/target/classes:$root/infra/persistence/target/classes:$root/infra/facade/target/classes:$root/infra/rest/target/classes:$root/start/target/classes:$root/shared/target/classes:$root/infra/external/target/classes:$root/infra/external/target/b23-gleif-deps/*"
exec java -cp "$runtime_classpath" io.github.archetom.crm.infra.external.gleif.runtime.B23GleifLiveCanaryRunner \
  --allow-live-canary "$report"
