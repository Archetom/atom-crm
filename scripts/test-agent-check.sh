#!/bin/sh
# Offline process checks: real Git fixtures, fake Maven/Docker/database gate.
set -eu
SCRIPT_DIR=$(CDPATH= cd -- "$(dirname -- "$0")" && pwd)
python3 "${SCRIPT_DIR}/test-agent-check.py"
