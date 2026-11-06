package com.paypulse.ledger.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** A balance holder. {@code @Version} gives optimistic locking, so two concurrent updates cannot both win (L1-3). */
@Entity
@Table(name = "accounts")
public class Account {

    @Id
    private UUID id;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balance = BigDecimal.ZERO.setScale(4);

    @Column(name = "credit_limit", nullable = false, precision = 19, scale = 4)
    private BigDecimal creditLimit = BigDecimal.ZERO.setScale(4);

    @Column(name = "credit_limit_expires_at")
    private Instant creditLimitExpiresAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private long version;

    protected Account() {
        // for JPA
    }

    public Account(UUID ownerId, String currency, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.ownerId = ownerId;
        this.currency = currency;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public UUID getOwnerId() { return ownerId; }
    public String getCurrency() { return currency; }
    public BigDecimal getBalance() { return balance; }
    public BigDecimal getCreditLimit() { return creditLimit; }
    public Instant getCreditLimitExpiresAt() { return creditLimitExpiresAt; }
    public Instant getCreatedAt() { return createdAt; }
    public long getVersion() { return version; }
}
