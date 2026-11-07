#!/usr/bin/env bash
# Break-it for card F1: start a throwaway Postgres and the real ledger-service, then send good and bad requests.
# Every bad request should get a clean ProblemDetail answer, never a 500 or a stack trace. Nothing is kept.
# Run: scripts/break-it-f1.sh   (needs Docker and Java 21)
set -u
cd "$(dirname "$0")/.."
export JAVA_HOME=${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}

DB=ledger-f1-db; PORT=8091; BASE=http://localhost:$PORT/api/v1/accounts
cleanup() { pkill -P "${APP_PID:-0}" 2>/dev/null; kill "${APP_PID:-0}" 2>/dev/null; kill $(lsof -ti "tcp:$PORT") 2>/dev/null; docker rm -f "$DB" >/dev/null 2>&1; }
trap cleanup EXIT
docker rm -f "$DB" >/dev/null 2>&1
docker run -d --rm --name "$DB" -e POSTGRES_PASSWORD=x -e POSTGRES_DB=ledger -p 55499:5432 postgres:16.15 >/dev/null || exit 1
until docker exec "$DB" pg_isready -U postgres >/dev/null 2>&1; do sleep 1; done

mvn -q -B -pl services/ledger-service -am install -DskipTests || exit 1
LEDGER_DB_URL=jdbc:postgresql://localhost:55499/ledger LEDGER_DB_USER=postgres LEDGER_DB_PASSWORD=x \
  mvn -q -B -pl services/ledger-service spring-boot:run -Dspring-boot.run.arguments=--server.port=$PORT >/tmp/ledger-f1.log 2>&1 &
APP_PID=$!
until curl -s -o /dev/null "http://localhost:$PORT/nothing"; do sleep 1; kill -0 $APP_PID 2>/dev/null || { echo "app did not start, see /tmp/ledger-f1.log"; exit 1; }; done

try() { # try <title> <curl args...>
  echo; echo "TRY: $1"; shift
  curl -s -w '\n  -> HTTP %{http_code}\n' "$@" | cut -c1-300
}
JSON=(-H 'Content-Type: application/json')
OWNER=$(uuidgen | tr A-Z a-z)

try "open a good account (expect 201)"            -X POST "$BASE" "${JSON[@]}" -d "{\"ownerId\":\"$OWNER\",\"currency\":\"EUR\"}"
try "lowercase currency (expect 400, field currency)" -X POST "$BASE" "${JSON[@]}" -d "{\"ownerId\":\"$OWNER\",\"currency\":\"eur\"}"
try "no owner (expect 400, field ownerId)"        -X POST "$BASE" "${JSON[@]}" -d '{"currency":"EUR"}'
try "broken JSON (expect 400)"                    -X POST "$BASE" "${JSON[@]}" -d '{"currency":'
try "id that is not a UUID (expect 400)"          "$BASE/not-a-uuid"
try "unknown account (expect 404, account-not-found)" -H 'X-Correlation-Id: demo-123' "$BASE/$(uuidgen | tr A-Z a-z)"
