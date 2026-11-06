package com.paypulse.ledger.persistence;

/** Which side of the books an entry is on. The amount is always positive; the direction carries the sign. */
public enum Direction {
    DEBIT,
    CREDIT
}
