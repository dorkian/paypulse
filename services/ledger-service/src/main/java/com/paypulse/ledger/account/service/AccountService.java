package com.paypulse.ledger.account.service;

import com.paypulse.ledger.account.api.AccountResponse;
import com.paypulse.ledger.account.api.OpenAccountRequest;
import com.paypulse.ledger.account.domain.AccountNotFoundException;
import com.paypulse.ledger.persistence.Account;
import com.paypulse.ledger.persistence.AccountRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.UUID;

/**
 * The account use cases (Pattern: Service layer). It owns the transaction boundary and returns
 * DTOs only, so the Account entity never reaches the web layer.
 */
@Service
public class AccountService {

    private static final Logger log = LoggerFactory.getLogger(AccountService.class);

    private final AccountRepository accounts;
    private final Clock clock;

    // Constructor injection lets a unit test build this with a mock repository and a fixed Clock.
    public AccountService(AccountRepository accounts, Clock clock) {
        this.accounts = accounts;
        this.clock = clock;
    }

    /** Opens an account with zero balance and zero credit limit, stamped with the injected clock. */
    @Transactional
    public AccountResponse openAccount(OpenAccountRequest request) {
        Account saved = accounts.save(new Account(request.ownerId(), request.currency(), clock.instant()));
        log.info("Account opened id={}", saved.getId());
        return AccountResponse.from(saved);
    }

    // readOnly: Hibernate skips dirty checking, and it documents that this never writes.
    @Transactional(readOnly = true)
    public AccountResponse getAccount(UUID id) {
        return accounts.findById(id)
                .map(AccountResponse::from)
                .orElseThrow(() -> new AccountNotFoundException(id));
    }
}
