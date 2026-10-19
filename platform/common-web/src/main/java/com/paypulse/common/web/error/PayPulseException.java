package com.paypulse.common.web.error;

import java.util.Objects;

/**
 * Base class for all expected business errors.
 * Subclasses (CustomerNotFoundException, ...) say what went wrong.
 * The error handler maps them to a ProblemDetail response.
 */
public abstract class PayPulseException extends RuntimeException {

    private final String code;
    private final ErrorCategory category;

    protected PayPulseException(String code, ErrorCategory category, String message) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.category = Objects.requireNonNull(category, "category");
    }

    /** Stable kebab-case id, for example "customer-not-found". Clients can rely on it. */
    public String getCode() {
        return code;
    }

    public ErrorCategory getCategory() {
        return category;
    }
}
