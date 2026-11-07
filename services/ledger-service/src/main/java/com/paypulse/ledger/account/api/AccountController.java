package com.paypulse.ledger.account.api;

import com.paypulse.ledger.account.service.AccountService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.UUID;

/**
 * The HTTP door of the account feature: it only translates HTTP to one service call and back.
 * Errors are thrown as exceptions and answered by the shared handler in common-web.
 */
@RestController
// Versioned path so a breaking change can ship later as /api/v2 without breaking clients.
@RequestMapping("/api/v1/accounts")
public class AccountController {

    private final AccountService service;

    public AccountController(AccountService service) {
        this.service = service;
    }

    @PostMapping
    public ResponseEntity<AccountResponse> open(@Valid @RequestBody OpenAccountRequest request) {
        AccountResponse opened = service.openAccount(request);

        // Built from the incoming request, so the URL stays correct behind the gateway.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(opened.id())
                .toUri();

        return ResponseEntity.created(location).body(opened);
    }

    // The UUID type means "not-a-uuid" is rejected with a 400 before our code runs.
    @GetMapping("/{id}")
    public AccountResponse get(@PathVariable UUID id) {
        return service.getAccount(id);
    }
}
