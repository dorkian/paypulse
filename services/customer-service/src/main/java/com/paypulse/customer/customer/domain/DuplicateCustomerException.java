package com.paypulse.customer.customer.domain;

import com.paypulse.common.web.error.ErrorCategory;
import com.paypulse.common.web.error.PayPulseException;

/**
 * Thrown when a customer with the same email already exists for this tenant.
 * The shared error handler turns CONFLICT into HTTP 409.
 */
public class DuplicateCustomerException extends PayPulseException {

    public DuplicateCustomerException() {
        // The message does NOT contain the email: it is personal data, and this text
        // goes into the response and the logs (S8.5).
        super("customer-email-taken", ErrorCategory.CONFLICT,
                "A customer with this email already exists");
    }
}
