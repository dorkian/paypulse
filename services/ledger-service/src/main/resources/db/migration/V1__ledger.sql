-- Ledger schema (card L1-1). Rules live in the database (S5.2) so no code path can break them.
-- Money is NUMERIC(19,4): exact decimal, never a float. See docs/adr/001-money-representation.md.

CREATE TABLE accounts (
    id                      UUID           PRIMARY KEY,
    owner_id                UUID           NOT NULL,
    currency                VARCHAR(3)     NOT NULL,
    balance                 NUMERIC(19, 4) NOT NULL DEFAULT 0,
    -- How far below zero the balance may go. 0 = no overdraft.
    credit_limit            NUMERIC(19, 4) NOT NULL DEFAULT 0,
    -- When the limit stops being valid. Time-based rules cannot be a CHECK (now() is not immutable),
    -- so the service treats an expired limit as 0 (card L1-2). The date itself is mandatory.
    credit_limit_expires_at TIMESTAMPTZ,
    created_at              TIMESTAMPTZ    NOT NULL,
    version                 BIGINT         NOT NULL DEFAULT 0,
    CONSTRAINT ck_accounts_currency_iso CHECK (currency ~ '^[A-Z]{3}$'),
    CONSTRAINT ck_accounts_credit_limit_non_negative CHECK (credit_limit >= 0),
    CONSTRAINT ck_accounts_balance_within_credit_limit CHECK (balance >= -credit_limit),
    CONSTRAINT ck_accounts_credit_limit_needs_expiry CHECK (credit_limit = 0 OR credit_limit_expires_at IS NOT NULL)
);

CREATE TABLE ledger_transactions (
    id              UUID         PRIMARY KEY,
    idempotency_key VARCHAR(100) NOT NULL,
    description     VARCHAR(255) NOT NULL,
    created_at      TIMESTAMPTZ  NOT NULL,
    CONSTRAINT uq_ledger_transactions_idempotency_key UNIQUE (idempotency_key)
);

CREATE TABLE ledger_entries (
    id             UUID           PRIMARY KEY,
    transaction_id UUID           NOT NULL,
    account_id     UUID           NOT NULL,
    amount         NUMERIC(19, 4) NOT NULL,
    direction      VARCHAR(10)    NOT NULL,
    created_at     TIMESTAMPTZ    NOT NULL,
    CONSTRAINT fk_ledger_entries_transaction FOREIGN KEY (transaction_id) REFERENCES ledger_transactions (id),
    CONSTRAINT fk_ledger_entries_account FOREIGN KEY (account_id) REFERENCES accounts (id),
    CONSTRAINT ck_ledger_entries_amount_positive CHECK (amount > 0),
    CONSTRAINT ck_ledger_entries_direction CHECK (direction IN ('DEBIT', 'CREDIT'))
);

CREATE INDEX ix_ledger_entries_transaction_id ON ledger_entries (transaction_id);
CREATE INDEX ix_ledger_entries_account_id ON ledger_entries (account_id);

-- Append-only: history is never edited. A mistake is fixed by posting a new reversing transaction.
CREATE FUNCTION forbid_change() RETURNS trigger AS $$
BEGIN
    RAISE EXCEPTION '% is append-only', TG_TABLE_NAME;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER trg_ledger_entries_append_only
    BEFORE UPDATE OR DELETE ON ledger_entries
    FOR EACH ROW EXECUTE FUNCTION forbid_change();

CREATE TRIGGER trg_ledger_transactions_append_only
    BEFORE UPDATE OR DELETE ON ledger_transactions
    FOR EACH ROW EXECUTE FUNCTION forbid_change();
