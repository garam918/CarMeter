#!/bin/sh
set -eu

PLEOS_PROJECT_ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)
. "$PLEOS_PROJECT_ROOT/scripts/env.sh"

exec "$ANDROID_HOME/emulator/emulator" \
    -avd "${PLEOS_AVD_NAME:-Pleos_Connect_2_1_0_API_34}" \
    -memory 4096 \
    -no-snapshot \
    "$@"

