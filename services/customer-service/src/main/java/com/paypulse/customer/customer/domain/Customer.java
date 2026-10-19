package com.paypulse.customer.customer.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.UUID;

/**
 * A customer of one merchant (tenant).
 *
 * <p>Pattern: Factory method. The only way to build a new Customer is {@link #create},
 * which cleans and checks the data, so an invalid or un-normalized Customer cannot exist.
 */
@Entity
@Table(name = "customers")
public class Customer {

    // The id is generated here in Java, not by the database (S3.5), so we know it
    // before the insert and can put it in logs, URLs and later in events.
    @Id
    private UUID id;

    // Which merchant owns this customer. Never changes after creation.
    @Column(name = "tenant_id", nullable = false, updatable = false)
    private UUID tenantId;

    @Column(nullable = false, length = 200)
    private String name;

    // Always stored trimmed + lowercase, so "Mario@Example.com" and "mario@example.com"
    // are the same customer for the unique constraint.
    @Column(nullable = false, length = 320)
    private String email;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // JPA (Hibernate) needs a no-arg constructor to build objects from rows.
    // protected = our own code can't use it by mistake, only JPA and subclasses can.
    protected Customer() {
    }

    // private: the only way in from outside is the factory below.
    private Customer(UUID id, UUID tenantId, String name, String email, Instant createdAt) {
        this.id = id;
        this.tenantId = tenantId;
        this.name = name;
        this.email = email;
        this.createdAt = createdAt;
    }

    /**
     * Builds a valid new Customer.
     *
     * @param now the current time, passed in by the caller (from an injected Clock).
     *            We never call Instant.now() here, so tests can use a fixed time (S3.4).
     */
    public static Customer create(UUID tenantId, String name, String email, Instant now) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(name, "name");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(now, "now");

        String cleanName = name.strip();
        if (cleanName.isEmpty()) {
            throw new IllegalArgumentException("name must not be blank");
        }
        // Locale.ROOT: lowercase by fixed rules. Without it, a computer set to Turkish
        // turns "I" into a dotless "ı" and the same email gets two different values.
        String cleanEmail = email.strip().toLowerCase(Locale.ROOT);
        if (cleanEmail.isEmpty()) {
            throw new IllegalArgumentException("email must not be blank");
        }

        return new Customer(UUID.randomUUID(), tenantId, cleanName, cleanEmail, now);
    }

    // Getters only. No setters: nobody can change a field and skip the rules above.
    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getName() {
        return name;
    }

    public String getEmail() {
        return email;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    // Two Customer objects are "the same" if they have the same id (S3.3).
    // instanceof (not getClass) because Hibernate sometimes hands out proxy subclasses.
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        return o instanceof Customer other && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return Objects.hashCode(id);
    }
}
