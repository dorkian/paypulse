package com.paypulse.ledger.account.domain;

import com.paypulse.common.web.error.ErrorCategory;
import com.paypulse.common.web.error.PayPulseException;

import java.util.UUID;

/**
 * Thrown when an account id does not exist. The shared handler in common-web turns NOT_FOUND
 * into a 404 ProblemDetail; this class knows nothing about HTTP.
 */
public class AccountNotFoundException extends PayPulseException {

    public AccountNotFoundException(UUID id) {
        // "account-not-found" is the stable code clients rely on. An id is not personal data.
        super("account-not-found", ErrorCategory.NOT_FOUND, "Account " + id + " was not found");
    }
}
