package com.paypulse.customer.customer.api;

import com.paypulse.customer.customer.domain.Customer;

import java.time.Instant;
import java.util.UUID;

/**
 * What the API sends back. A separate type from the entity (S1.4), so the JSON shape
 * is a deliberate contract and a change to the table can't leak into the API by accident.
 */
public record CustomerResponse(
        UUID id,
        UUID tenantId,
        String name,
        String email,
        Instant createdAt
) {

    /** Converts an entity into the response. The single place this mapping lives. */
    public static CustomerResponse from(Customer customer) {
        return new CustomerResponse(
                customer.getId(),
                customer.getTenantId(),
                customer.getName(),
                customer.getEmail(),
                customer.getCreatedAt());
    }
}

