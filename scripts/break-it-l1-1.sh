#!/usr/bin/env bash
# Break-it for card L1-1: start a throwaway Postgres, load the ledger schema, then try to cheat.
# Each attempt should be REFUSED by the database. Nothing is saved: the container is removed at the end.
# Run: scripts/break-it-l1-1.sh   (needs Docker running)
set -u
cd "$(dirname "$0")/.."

MIGRATION=services/ledger-service/src/main/resources/db/migration/V1__ledger.sql
NAME=ledger-break-it
docker rm -f "$NAME" >/dev/null 2>&1
docker run -d --rm --name "$NAME" -e POSTGRES_PASSWORD=x postgres:16.15 >/dev/null || exit 1
trap 'docker rm -f "$NAME" >/dev/null 2>&1' EXIT

until docker exec "$NAME" psql -U postgres -tAc "select 1" >/dev/null 2>&1; do sleep 1; done
sql() { docker exec -i "$NAME" psql -U postgres -v ON_ERROR_STOP=1 -q "$@"; }
sql <"$MIGRATION" || exit 1

# One valid account, one valid transaction, one valid entry to attack.
sql <<'SQL'
INSERT INTO accounts (id, owner_id, currency, created_at)
  VALUES ('00000000-0000-0000-0000-000000000001', gen_random_uuid(), 'EUR', now());
INSERT INTO ledger_transactions (id, idempotency_key, description, created_at)
  VALUES ('00000000-0000-0000-0000-0000000000a1', 'key-1', 'first', now());
INSERT INTO ledger_entries (id, transaction_id, account_id, amount, direction, created_at)
  VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1',
          '00000000-0000-0000-0000-000000000001', 10.00, 'DEBIT', now());
SQL

attempt() {
  echo; echo "TRY: $1"
  if out=$(sql -c "$2" 2>&1); then echo "  !! ACCEPTED (this is a bug)"; else echo "  refused: $(head -1 <<<"$out")"; fi
}

attempt "edit a ledger entry (change 10 to 1000)"       "UPDATE ledger_entries SET amount = 1000;"
attempt "delete a ledger entry"                         "DELETE FROM ledger_entries;"
attempt "reuse the idempotency key"                     "INSERT INTO ledger_transactions VALUES (gen_random_uuid(), 'key-1', 'dup', now());"
attempt "enter a negative amount"                       "INSERT INTO ledger_entries VALUES (gen_random_uuid(), '00000000-0000-0000-0000-0000000000a1', '00000000-0000-0000-0000-000000000001', -5, 'DEBIT', now());"
attempt "overdraw an account with no credit limit"      "UPDATE accounts SET balance = -1;"
attempt "use a lowercase currency"                      "INSERT INTO accounts (id, owner_id, currency, created_at) VALUES (gen_random_uuid(), gen_random_uuid(), 'eur', now());"
