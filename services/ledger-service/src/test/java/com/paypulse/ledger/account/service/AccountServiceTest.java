package com.paypulse.ledger.account.service;

import com.paypulse.ledger.account.api.AccountResponse;
import com.paypulse.ledger.account.api.OpenAccountRequest;
import com.paypulse.ledger.account.domain.AccountNotFoundException;
import com.paypulse.ledger.persistence.Account;
import com.paypulse.ledger.persistence.AccountRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/** Unit test of the account use cases: no Spring, no database, a frozen clock (S11.1, S11.4). */
@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-07T10:00:00Z");

    @Mock
    AccountRepository repository;

    private AccountService service() {
        return new AccountService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void shouldStampTheNewAccountWithTheInjectedClock() {
        when(repository.save(any(Account.class))).thenAnswer(call -> call.getArgument(0));
        UUID owner = UUID.randomUUID();

        AccountResponse opened = service().openAccount(new OpenAccountRequest(owner, "EUR"));

        assertThat(opened.createdAt()).isEqualTo(NOW);
        assertThat(opened.ownerId()).isEqualTo(owner);
        assertThat(opened.currency()).isEqualTo("EUR");
    }

    @Test
    void shouldThrowAccountNotFoundWhenTheIdDoesNotExist() {
        UUID missing = UUID.randomUUID();
        when(repository.findById(missing)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service().getAccount(missing))
                .isInstanceOf(AccountNotFoundException.class)
                .hasMessageContaining(missing.toString());
    }
}
