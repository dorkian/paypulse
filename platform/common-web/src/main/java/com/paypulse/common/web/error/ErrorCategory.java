package com.paypulse.common.web.error;

/**
 * What kind of problem happened, in domain terms.
 * Services pick a category. Only the error handler turns it into an HTTP status.
 */
public enum ErrorCategory {
    /** The thing asked for does not exist. */
    NOT_FOUND,
    /** The request clashes with existing data (for example a duplicate). */
    CONFLICT,
    /** The request is well formed but a business rule forbids it. */
    BUSINESS_RULE
}
