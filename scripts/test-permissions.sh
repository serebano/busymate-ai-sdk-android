#!/bin/bash
set -euo pipefail
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
cd "$ROOT"
BUSYMATE_RUNTIME_MODE=permissions exec bash scripts/test-runtime.sh
