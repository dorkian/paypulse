package com.paypulse.ledger.account.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.util.UUID;

/**
 * What a client sends to open an account (Pattern: DTO). Bean Validation runs on @Valid in the
 * controller, so a bad body becomes a 400 with a field list before any service code runs.
 */
public record OpenAccountRequest(

        @NotNull
        UUID ownerId,

        // @Pattern accepts null, so @NotBlank is what rejects a missing currency.
        // Three upper-case letters is the ISO 4217 shape and matches the CHAR(3) column.
        @NotBlank
        @Pattern(regexp = "^[A-Z]{3}$")
        String currency
) {
}
