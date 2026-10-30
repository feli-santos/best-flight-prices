#!/usr/bin/env bash
# Starts/stops the local lab backend (fake provider + server) as background processes. Logs in .local/.
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
mkdir -p .local
case "${1:-}" in
  up)
    ./gradlew :server:installDist :fake-provider:installDist -q
    "$0" down
    FAKE_PORT=8090 nohup fake-provider/build/install/fake-provider/bin/fake-provider > .local/fake.log 2>&1 &
    sleep 3
    FARE_PROVIDER=travelpayouts TRAVELPAYOUTS_TOKEN=lab TRAVELPAYOUTS_BASE_URL=http://127.0.0.1:8090 \
      RATE_LIMIT_PER_MINUTE=600 PORT=8080 nohup server/build/install/server/bin/server > .local/server.log 2>&1 &
    for i in $(seq 1 30); do curl -fs localhost:8080/v1/health >/dev/null && { echo "lab up: server :8080, fake provider :8090"; exit 0; }; sleep 1; done
    echo "lab failed to start; see .local/server.log" >&2; exit 1 ;;
  down)
    pkill -f "bfp.server.MainKt" 2>/dev/null || true
    pkill -f "bfp.fake.MainKt" 2>/dev/null || true ;;
  scenario) curl -fsS -X POST "localhost:8090/__scenario/${2:?name}" ;;
  *) echo "usage: $0 up|down|scenario <name>" >&2; exit 2 ;;
esac
