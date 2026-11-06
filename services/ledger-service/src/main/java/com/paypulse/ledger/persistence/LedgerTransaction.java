package com.paypulse.ledger.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.Instant;
import java.util.UUID;

/** One posted movement of money. Append-only: the database rejects any update or delete. */
@Entity
@Immutable
@Table(name = "ledger_transactions")
public class LedgerTransaction {

    @Id
    private UUID id;

    @Column(name = "idempotency_key", nullable = false, length = 100)
    private String idempotencyKey;

    @Column(nullable = false)
    private String description;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected LedgerTransaction() {
        // for JPA
    }

    public LedgerTransaction(String idempotencyKey, String description, Instant createdAt) {
        this.id = UUID.randomUUID();
        this.idempotencyKey = idempotencyKey;
        this.description = description;
        this.createdAt = createdAt;
    }

    public UUID getId() { return id; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public String getDescription() { return description; }
    public Instant getCreatedAt() { return createdAt; }
}
