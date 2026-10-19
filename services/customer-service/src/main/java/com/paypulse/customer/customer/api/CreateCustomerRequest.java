package com.paypulse.customer.customer.api;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

/**
 * What a client sends to create a customer. A record = an immutable data carrier.
 * Bean Validation checks it when the controller uses @Valid (B10). A bad request
 * becomes a 400 with a field list, handled by common-web, before any service code runs.
 *
 * @param tenantId TEMPORARY: comes from the request body until L2. From L2 on it is
 *                 read from the login token, and this field is removed. Never trust a
 *                 client-supplied tenant in production.
 */
public record CreateCustomerRequest(

        @NotNull
        UUID tenantId,

        // Max sizes match the VARCHAR columns in V1, so the client gets a clear 400
        // instead of a database error.
        @NotBlank
        @Size(max = 200)
        String name,

        // @Email alone accepts an empty string, so @NotBlank is needed too.
        @NotBlank
        @Email
        @Size(max = 320)
        String email
) {
}

