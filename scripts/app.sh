#!/usr/bin/env bash
# Runs BestFlightPrice on the desktop: starts the backend, opens the app, stops the backend on exit.
#   scripts/app.sh          sample data (clearly labeled in the app); uses real fares if .env has a token
#   scripts/app.sh sample   force sample data
#   scripts/app.sh dev      same, plus the dev panel (language, dark mode, text size)
set -euo pipefail
cd "$(dirname "$0")/.."
export JAVA_HOME="${JAVA_HOME:-/opt/homebrew/opt/openjdk@21/libexec/openjdk.jdk/Contents/Home}"
mkdir -p .local
mode="${1:-auto}"
if [ -f .env ] && [ "$mode" != "sample" ]; then set -a; . ./.env; set +a; fi
if [ "$mode" = "sample" ] || [ -z "${TRAVELPAYOUTS_TOKEN:-}" ]; then
  export FARE_PROVIDER=sample; echo "Using SAMPLE data (made-up prices). Add TRAVELPAYOUTS_TOKEN to .env for real fares."
else
  export FARE_PROVIDER=travelpayouts; echo "Using real Travelpayouts fares."
fi
export PORT="${PORT:-8080}"
pkill -f "bfp.server.MainKt" 2>/dev/null || true
./gradlew :server:installDist -q
server/build/install/server/bin/server > .local/server.log 2>&1 &
SERVER_PID=$!
trap 'kill $SERVER_PID 2>/dev/null || true' EXIT
for i in $(seq 1 30); do curl -fs "localhost:$PORT/v1/health" >/dev/null && break; sleep 1; done
curl -fs "localhost:$PORT/v1/health" >/dev/null || { echo "backend failed; see .local/server.log" >&2; exit 1; }
[ "$mode" = "dev" ] && export BFP_DEVPANEL=1
BFP_BASE_URL="http://localhost:$PORT" ./gradlew :desktopApp:run -q
