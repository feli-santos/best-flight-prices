#!/usr/bin/env bash
# Runs the server against the real Travelpayouts Data API for structural checks. Never asserts on prices.
# Needs TRAVELPAYOUTS_TOKEN in .env. Logs in .local/server-real.log; stop with make lab-down.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
port="${PORT:-8080}"
mkdir -p .local
[ -f .env ] || {
    echo "missing .env: cp .env.example .env and set TRAVELPAYOUTS_TOKEN" >&2
    exit 1
}
set -a
. ./.env
set +a
[ -n "${TRAVELPAYOUTS_TOKEN:-}" ] || {
    echo "TRAVELPAYOUTS_TOKEN is empty in .env" >&2
    exit 1
}
./gradlew :server:installDist -q
pkill -f "bfp.server.MainKt" 2>/dev/null || true
FARE_PROVIDER=travelpayouts PORT="$port" nohup server/build/install/server/bin/server >.local/server-real.log 2>&1 &
for i in $(seq 1 30); do
    if curl -fs "localhost:$port/v1/health" >/dev/null; then
        echo "real fares up on :$port (provider=travelpayouts); stop with make lab-down"
        exit 0
    fi
    sleep 1
done
echo "server failed to start; see .local/server-real.log" >&2
exit 1
