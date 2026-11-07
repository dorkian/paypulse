package com.paypulse.ledger.account.api;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.paypulse.ledger.persistence.Account;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.UUID;

/**
 * What the API sends back for an account (Pattern: DTO). A separate type from the entity, so the
 * JSON shape is a deliberate contract and a column change cannot leak into the API.
 */
public record AccountResponse(
        UUID id,
        UUID ownerId,
        String currency,
        // A JSON string, never a float: a float would silently lose cents in some clients.
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal balance,
        @JsonFormat(shape = JsonFormat.Shape.STRING)
        BigDecimal creditLimit,
        Instant createdAt
) {

    // Matches NUMERIC(19,4), so "0.0000" is always printed, whatever scale the value arrived with.
    private static final int MONEY_SCALE = 4;

    public AccountResponse {
        balance = balance.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
        creditLimit = creditLimit.setScale(MONEY_SCALE, RoundingMode.HALF_EVEN);
    }

    /** Converts an entity into the response. The single place this mapping lives. */
    public static AccountResponse from(Account account) {
        return new AccountResponse(
                account.getId(),
                account.getOwnerId(),
                account.getCurrency(),
                account.getBalance(),
                account.getCreditLimit(),
                account.getCreatedAt());
    }
}
