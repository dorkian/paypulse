package com.paypulse.ledger.persistence;

import com.paypulse.ledger.PostgresTestBase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests the database rules of the ledger (V1__ledger.sql) with plain SQL. Constraints and triggers
 * live in the database (S5.2) so that no code path, now or later, can break them. Each rejection test
 * also checks the constraint NAME in the error, so a test cannot pass for the wrong reason.
 */
class LedgerSchemaTest extends PostgresTestBase {

    @Autowired
    JdbcTemplate jdbc;

    // --- helpers: insert valid rows, so each test breaks exactly ONE rule ---

    private UUID account(String currency, String balance) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, owner_id, currency, balance, created_at) VALUES (?, ?, ?, ?, now())",
                id, UUID.randomUUID(), currency, new BigDecimal(balance));
        return id;
    }

    // expiresAtSql is a SQL expression ("NULL" or "now() + interval '30 days'"), never user input.
    private UUID accountWithLimit(String balance, String creditLimit, String expiresAtSql) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO accounts (id, owner_id, currency, balance, credit_limit, credit_limit_expires_at, created_at) "
                        + "VALUES (?, ?, 'EUR', ?, ?, " + expiresAtSql + ", now())",
                id, UUID.randomUUID(), new BigDecimal(balance), new BigDecimal(creditLimit));
        return id;
    }

    private UUID transaction(String idempotencyKey) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ledger_transactions (id, idempotency_key, description, created_at) VALUES (?, ?, 'test', now())",
                id, idempotencyKey);
        return id;
    }

    private UUID entry(UUID transactionId, UUID accountId, String amount, String direction) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO ledger_entries (id, transaction_id, account_id, amount, direction, created_at) VALUES (?, ?, ?, ?, ?, now())",
                id, transactionId, accountId, new BigDecimal(amount), direction);
        return id;
    }

    private String key() {
        return "key-" + UUID.randomUUID();
    }

    // --- the happy path: proves the helpers work, so the rejections below are rejections of the RULE ---

    @Test
    void migrationCreatesTheThreeTables() {
        // Flyway ran V1 at startup: all three tables exist.
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public'", String.class);
        assertThat(tables).contains("accounts", "ledger_transactions", "ledger_entries");
    }

    @Test
    void aValidAccountTransactionAndEntryAreAccepted() {
        UUID account = account("EUR", "100.0000");
        UUID tx = transaction(key());
        entry(tx, account, "25.5000", "DEBIT");
        // amount keeps four decimals exactly: this is why money is NUMERIC, not a float.
        BigDecimal stored = jdbc.queryForObject("SELECT balance FROM accounts WHERE id = ?", BigDecimal.class, account);
        assertThat(stored).isEqualByComparingTo("100.0000");
        assertThat(stored.scale()).isEqualTo(4);
    }

    // --- accounts ---

    @Test
    void anAccountWithoutALimitCannotGoNegative() {
        // Default credit limit is 0: the last line of defence against overdrawing, even if the service has a bug.
        assertThatThrownBy(() -> account("EUR", "-0.0001"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_balance_within_credit_limit");
    }

    @Test
    void anAccountMayGoNegativeUpToItsCreditLimitButNotBeyond() {
        // Exactly at the limit is fine, one cent-fraction past it is not.
        accountWithLimit("-500.0000", "500.0000", "now() + interval '30 days'");
        assertThatThrownBy(() -> accountWithLimit("-500.0001", "500.0000", "now() + interval '30 days'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_balance_within_credit_limit");
    }

    @Test
    void aCreditLimitCannotBeNegative() {
        // Balance 5 keeps the "within limit" rule satisfied, so only the negative limit is wrong.
        assertThatThrownBy(() -> accountWithLimit("5", "-1", "now() + interval '30 days'"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_credit_limit_non_negative");
    }

    @Test
    void aCreditLimitMustHaveAnExpiryDate() {
        // A limit that never expires is a permanent loan: the expiry date is mandatory when the limit is above zero.
        assertThatThrownBy(() -> accountWithLimit("0", "500.0000", "NULL"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_credit_limit_needs_expiry");
        // And a zero limit needs no expiry.
        accountWithLimit("0", "0", "NULL");
    }

    @Test
    void aCurrencyMustBeThreeUppercaseLetters() {
        // "eur" and "EU" are typos that would silently create a second, separate currency.
        assertThatThrownBy(() -> account("eur", "0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_currency_iso");
        assertThatThrownBy(() -> account("EU", "0"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_accounts_currency_iso");
    }

    @Test
    void aNewAccountStartsAtVersionZero() {
        // @Version (optimistic locking, card L1-3) needs a numeric start value.
        UUID account = account("EUR", "0");
        Long version = jdbc.queryForObject("SELECT version FROM accounts WHERE id = ?", Long.class, account);
        assertThat(version).isZero();
    }

    // --- transactions ---

    @Test
    void theSameIdempotencyKeyCannotBeUsedTwice() {
        // Retrying a request must not post the money twice: the database has the final say in a race.
        String key = key();
        transaction(key);
        assertThatThrownBy(() -> transaction(key))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("uq_ledger_transactions_idempotency_key");
    }

    // --- entries ---

    @Test
    void anEntryAmountMustBePositive() {
        // The sign lives in `direction`, never in the amount. Zero and negative amounts are rejected.
        UUID account = account("EUR", "0");
        UUID tx = transaction(key());
        assertThatThrownBy(() -> entry(tx, account, "0", "DEBIT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_entries_amount_positive");
        assertThatThrownBy(() -> entry(tx, account, "-5", "CREDIT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_entries_amount_positive");
    }

    @Test
    void aDirectionMustBeDebitOrCredit() {
        UUID account = account("EUR", "0");
        UUID tx = transaction(key());
        assertThatThrownBy(() -> entry(tx, account, "1", "SIDEWAYS"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("ck_ledger_entries_direction");
    }

    @Test
    void anEntryMustPointToAnExistingTransactionAndAccount() {
        UUID account = account("EUR", "0");
        UUID tx = transaction(key());
        assertThatThrownBy(() -> entry(UUID.randomUUID(), account, "1", "DEBIT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_ledger_entries_transaction");
        assertThatThrownBy(() -> entry(tx, UUID.randomUUID(), "1", "DEBIT"))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("fk_ledger_entries_account");
    }

    // --- append-only: a journal is history, and history is not edited ---

    @Test
    void entriesCannotBeUpdatedOrDeleted() {
        UUID account = account("EUR", "0");
        UUID tx = transaction(key());
        UUID entry = entry(tx, account, "10", "DEBIT");
        // A mistake is fixed by posting a NEW reversing transaction, never by editing the old row.
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_entries SET amount = 99 WHERE id = ?", entry))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ledger_entries is append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_entries WHERE id = ?", entry))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ledger_entries is append-only");
    }

    @Test
    void transactionsCannotBeUpdatedOrDeleted() {
        UUID tx = transaction(key());
        assertThatThrownBy(() -> jdbc.update("UPDATE ledger_transactions SET description = 'edited' WHERE id = ?", tx))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ledger_transactions is append-only");
        assertThatThrownBy(() -> jdbc.update("DELETE FROM ledger_transactions WHERE id = ?", tx))
                .isInstanceOf(DataAccessException.class)
                .hasMessageContaining("ledger_transactions is append-only");
    }
}
