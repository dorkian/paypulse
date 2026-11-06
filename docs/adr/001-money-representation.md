# ADR-001: How money is stored

Status: accepted (2026-11-06, card L1-1)

## Decision

- Amounts and balances are `NUMERIC(19,4)` in Postgres and `BigDecimal` in Java. Never `float`, `double` or `real`.
- Every account has exactly one ISO 4217 currency, stored as three uppercase letters (`EUR`). The database rejects anything else.
- Rounding, when the code must round, is `RoundingMode.HALF_EVEN` (banker's rounding), done explicitly in Java.
- An entry amount is always positive. `direction` (`DEBIT` or `CREDIT`) carries the sign.
- A balance may go below zero only down to `-credit_limit`. The limit is 0 by default, and a limit above 0 must have an expiry date.

## Why NUMERIC and not integer minor units

Integer cents are fast and exact, but they bake in "2 decimals", which is wrong for some currencies (JPY has 0, KWD has 3) and for intermediate results such as fees and FX. `NUMERIC(19,4)` is exact, has headroom for those, and reads naturally in SQL. The cost is `BigDecimal` in Java, which is accepted.

## Pitfalls this decision guards against

- **Floats lose cents.** `0.1 + 0.2` is not `0.3`. NUMERIC is exact decimal.
- **Postgres rounds silently.** Inserting `1.23456` into `NUMERIC(19,4)` stores `1.2346` with no error. Code must round on purpose before it writes, so rounding is a visible decision.
- **Scale matters in Java.** `new BigDecimal("1.0").equals(new BigDecimal("1.00"))` is false. Compare with `compareTo`, never `equals`.
- **Mixed currencies.** Adding EUR to USD is a bug. A transaction posts only between accounts of the same currency until FX exists.

## Credit limits and expiry

A limit and its expiry date live on the account. "The limit has expired" depends on the current time, which a database CHECK cannot use (`now()` is not immutable). So the database guarantees `balance >= -credit_limit` and "a limit always has an expiry date", and the service (card L1-2) treats an expired limit as 0 when it posts a transaction.

Open consequence: if a limit expires while the balance is below zero, the account is in arrears. The database does not block this (nothing is being posted). What happens next (block further debits, collect, notify) is a business rule for a later card.

## Consequences

- Every money column uses the same type, so sums and comparisons never mix scales by accident.
- The journal (`ledger_entries`, `ledger_transactions`) is append-only by trigger. Corrections are new reversing transactions.
- "Debits equal credits in one transaction" is enforced in the service (L1-2), not in the schema.
