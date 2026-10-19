package com.paypulse.customer.customer.domain;

import com.paypulse.common.web.error.ErrorCategory;
import com.paypulse.common.web.error.PayPulseException;

import java.util.UUID;

/**
 * Thrown when a customer id does not exist.
 * The shared error handler (common-web) turns NOT_FOUND into HTTP 404.
 * This class knows nothing about HTTP: it only says what went wrong.
 */
public class CustomerNotFoundException extends PayPulseException {

    public CustomerNotFoundException(UUID id) {
        // "customer-not-found" is the stable code clients can rely on.
        // An id is safe to show. It is not personal data.
        super("customer-not-found", ErrorCategory.NOT_FOUND, "Customer " + id + " was not found");
    }
}
