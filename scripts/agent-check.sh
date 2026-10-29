#!/usr/bin/env bash
# CRM verification adapted from atom-archetype 2.3.0. Invoke from the project root.
set -euo pipefail
# Preserve the report channel even if a signal arrives inside a stderr-redirected check.
exec 3>&1

scope=${1:-full}
selected_scope=$scope
selected_module=
selection_reason=explicit-scope
context=unknown
status=failed
checks=()
all_report_paths=()
reports_directory=
active_check=
active_started=0
active_report_root=
active_marker=
pending_report_root=
started=$SECONDS
check_number=0

json_string() {
    local value=$1 character number index LC_ALL=C
    printf '"'
    for ((index=0; index<${#value}; index++)); do
        character=${value:index:1}
        case "$character" in
            '"') printf '\\"' ;;
            '\') printf '\\\\' ;;
            [[:cntrl:]]) printf -v number '%d' "'$character"; printf '\\u%04x' "$number" ;;
            *) printf '%s' "$character" ;;
        esac
    done
    printf '"'
}

json_paths() {
    local separator= path
    printf '['
    for path in "$@"; do
        printf '%s' "$separator"
        json_string "$path"
        separator=,
    done
    printf ']'
}

record() {
    local name=$1 result=$2 duration=$3 code=$4 report_status=$5
    shift 5
    checks+=("{\"name\":$(json_string "$name"),\"status\":$(json_string "$result"),\"durationMs\":$duration,\"exitCode\":$code,\"reportStatus\":$(json_string "$report_status"),\"reportPaths\":$(json_paths "$@")}")
}

# Snapshot only XML reports written during this check. Later Maven clean/test runs
# may remove or overwrite the originals. Old reports never certify a new test run.
snapshot_reports() {
    local root=$1 marker=$2 name=$3 list source relative destination directory seen previous
    report_paths=()
    report_status=missing
    [ -d "$root" ] && [ -f "$marker" ] || return 1
    root=$(cd "$root" && pwd -P) || return 1
    list=$(mktemp "${TMPDIR:-/tmp}/atom-agent-report-list.XXXXXX") || return 1
    if ! find "$root" -type d -name .git -prune -o -type f -newer "$marker" \
        \( -path '*/target/surefire-reports/TEST-*.xml' -o -path '*/target/failsafe-reports/TEST-*.xml' \) \
        -size +0c -print0 > "$list"; then
        rm -f -- "$list"
        return 1
    fi
    while IFS= read -r -d '' source; do
        if [ -z "$reports_directory" ]; then
            reports_directory=$(mktemp -d "${TMPDIR:-/tmp}/atom-agent-reports.XXXXXX") || { rm -f -- "$list"; return 1; }
            reports_directory=$(cd "$reports_directory" && pwd -P) || { rm -f -- "$list"; return 1; }
        fi
        relative=${source#"$root"/}
        destination="$reports_directory/$check_number-$name/$relative"
        directory=$(dirname "$destination")
        mkdir -p "$directory" && cp "$source" "$destination" || { rm -f -- "$list"; return 1; }
        seen=false
        for previous in "${report_paths[@]+"${report_paths[@]}"}"; do
            [ "$previous" != "$directory" ] || seen=true
        done
        if [ "$seen" = false ]; then
            report_paths+=("$directory")
            all_report_paths+=("$directory")
        fi
    done < "$list"
    rm -f -- "$list" || return 1
    if [ "${#report_paths[@]}" -gt 0 ]; then report_status=available; fi
}

complete_check() {
    local code=$1 result=passed duration=$(( (SECONDS - active_started) * 1000 ))
    report_paths=()
    report_status=not-requested
    if [ -n "$active_report_root" ]; then
        if ! snapshot_reports "$active_report_root" "$active_marker" "$active_check"; then
            report_status=unavailable
        fi
        if [ "$report_status" != available ] && [ "$code" -eq 0 ]; then
            code=3
            printf '[agent-check] %s: no fresh test reports were available; test execution is unverified.\n' "$active_check" >&2
        fi
    fi
    [ -z "$active_marker" ] || rm -f -- "$active_marker"
    [ "$code" -eq 0 ] || result=failed
    record "$active_check" "$result" "$duration" "$code" "$report_status" "${report_paths[@]+"${report_paths[@]}"}"
    active_check=
    active_report_root=
    active_marker=
    completion_code=$code
}

finish() {
    local code=$? separator= entry
    trap - EXIT
    exec 1>&3
    set +e
    if [ -n "$active_check" ]; then
        complete_check "$code"
        code=$completion_code
    fi
    if [ "$code" -ne 0 ] && [ "$status" != blocked ]; then status=failed; fi
    printf '{"status":"%s","context":"%s","scope":"%s",' "$status" "$context" "$scope"
    printf '"selectedScope":%s,"selectedModules":' "$(json_string "$selected_scope")"
    if [ -n "$selected_module" ]; then json_paths "$selected_module"; else printf '[]'; fi
    printf ',"selectionReason":%s,"durationMs":%s,"timeResolutionMs":1000,"exitCode":%s,' \
        "$(json_string "$selection_reason")" "$(( (SECONDS - started) * 1000 ))" "$code"
    printf '"reportPaths":'
    json_paths "${all_report_paths[@]+"${all_report_paths[@]}"}"
    printf ',"retainedReportsPath":'
    if [ -n "$reports_directory" ]; then json_string "$reports_directory"; else printf 'null'; fi
    printf ',"retainedProjectPath":'
    printf 'null'
    printf ',"checks":['
    for entry in "${checks[@]+"${checks[@]}"}"; do
        printf '%s%s' "$separator" "$entry"
        separator=,
    done
    printf ']}\n'
    exit "$code"
}
trap finish EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

run() {
    local name=$1 code=0
    shift
    active_check=$name
    active_started=$SECONDS
    active_report_root=$pending_report_root
    pending_report_root=
    check_number=$((check_number + 1))
    if [ -n "$active_report_root" ]; then
        active_marker=$(mktemp "${TMPDIR:-/tmp}/atom-agent-report-start.XXXXXX")
    fi
    printf '[agent-check] %s\n' "$name" >&2
    "$@" >&2 || code=$?
    complete_check "$code"
    return "$completion_code"
}

run_tests() {
    local name=$1
    pending_report_root=$2
    shift 2
    run "$name" "$@"
}

case "$scope" in
    docs|generate|fast|full|affected) ;;
    *) scope=unknown; selected_scope=unknown; record scope failed 0 2 not-requested
       printf 'Expected SCOPE=docs, fast, full, or affected.\n' >&2
       exit 2 ;;
esac

if [ -f pom.xml ] && [ -f mvnw ] && [ -d start ] && [ -d domain ]; then
    context=application
else
    record project-root failed 0 2 not-requested
    printf 'Run agent-check from the CRM project root.\n' >&2
    exit 2
fi

if [ "$scope" = generate ]; then
    record scope failed 0 2 not-requested
    printf 'SCOPE=generate is only available in the archetype repository.\n' >&2
    exit 2
fi

if [ "$scope" = affected ]; then
    selected_scope=full
    selection_reason=selector-unavailable
    selector="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)/affected-scope.py"
    if command -v python3 >/dev/null 2>&1 && [ -f "$selector" ]; then
        if selection=$(python3 "$selector" "$context"); then
            read -r selected_scope selected_module selection_reason <<< "$selection"
            [ "$selected_module" != - ] || selected_module=
            case "$selected_scope:$selected_module" in
                docs:|fast:|full:|module-tests:domain|module-tests:application) ;;
                *) selected_scope=full; selected_module=; selection_reason=invalid-selector-result ;;
            esac
        fi
    fi
    printf '[agent-check] affected selected %s (%s)\n' "$selected_scope" "$selection_reason" >&2
fi

check_whitespace() {
    git diff --check -- . && git diff --cached --check -- .
}

# A nested project must not check its parent checkout's diff.
git_root=$(git rev-parse --show-toplevel 2>/dev/null || true)
if [ -n "$git_root" ] && [ "$(cd "$git_root" && pwd -P)" = "$(pwd -P)" ]; then
    run changed-file-whitespace check_whitespace
else
    record changed-file-whitespace skipped 0 null not-requested
    if [ "$selected_scope" = docs ]; then
        record docs-change-context blocked 0 2 not-requested
        status=blocked
        printf 'Docs checks need an independent Git project to identify changed files. Check links and facts manually.\n' >&2
        exit 2
    fi
fi

if [ "$selected_scope" = docs ]; then
    printf 'Docs scope checks staged and unstaged whitespace only. Review links and facts.\n' >&2
    status=passed
    exit 0
fi

require_docker() {
    local began=$SECONDS code=0
    command -v docker >/dev/null 2>&1 && docker info >/dev/null 2>&1 || code=$?
    if [ "$code" -eq 0 ]; then
        record docker passed "$(( (SECONDS - began) * 1000 ))" 0 not-requested
    else
        record docker blocked "$(( (SECONDS - began) * 1000 ))" "$code" not-requested
        status=blocked
        printf 'Docker is unavailable. Required integration tests have not passed; start Docker and rerun SCOPE=full.\n' >&2
        exit 2
    fi
}

application_test() {
    local directory=$1
    shift
    # CRM's database condition does not use CI; only this property skips database tests.
    (cd "$directory" && sh ./mvnw -B -ntp -Datom.test.skip-database-tests=true "$@" test)
}

if [ "$selected_scope" = module-tests ]; then
    run_tests affected-module-tests . application_test . -pl "$selected_module" -am
elif [ "$selected_scope" = full ]; then
    require_docker
    # clean install already runs unit, architecture and database tests. The release
    # verifier also rejects skipped/missing database coverage, so do not run them twice.
    run_tests release-verification . sh scripts/release-verify.sh
else
    run_tests unit-and-architecture-tests . application_test .
fi

if [ "$selected_scope" != full ]; then
    # Kept for consumers of the original report schema; this means explicitly skipped.
    record docker-integration-tests not-requested 0 null not-requested
    printf 'Docker integration tests were not requested by this scope.\n' >&2
fi
status=passed
