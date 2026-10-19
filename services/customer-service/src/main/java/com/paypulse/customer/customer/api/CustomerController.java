package com.paypulse.customer.customer.api;

import com.paypulse.customer.customer.service.CustomerService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

/**
 * The HTTP door of the customer feature. It only translates HTTP to a service call and
 * back: no business rules, no repository, no try/catch (S1.1). Errors are thrown as
 * exceptions and answered by the shared handler in common-web.
 */
@RestController
// /api/v1: the version is in the path, so a breaking change can ship later as /api/v2
// without breaking existing clients (S4.1). Plural noun, no verbs (S4.2).
@RequestMapping("/api/v1/customers")
public class CustomerController {

    private final CustomerService service;

    public CustomerController(CustomerService service) {
        this.service = service;
    }

    // 201 Created + a Location header that tells the client where the new resource lives.
    // @Valid runs the checks on CreateCustomerRequest; on failure Spring throws
    // MethodArgumentNotValidException and common-web answers 400 with the field list.
    @PostMapping
    public ResponseEntity<CustomerResponse> create(@Valid @RequestBody CreateCustomerRequest request) {
        CustomerResponse created = service.createCustomer(request);

        // Builds http://host/api/v1/customers/{id} from the request that just arrived,
        // so it is correct behind a gateway or on another port. Nothing hard-coded.
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}")
                .buildAndExpand(created.id())
                .toUri();

        return ResponseEntity.created(location).body(created);
    }

    // The UUID type in the path means "not-a-uuid" is rejected before our code runs.
    @GetMapping("/{id}")
    public CustomerResponse get(@PathVariable UUID id) {
        return service.getCustomer(id);
    }

    // GET /api/v1/customers?limit=20. The service applies the default and the cap,
    // so the rule exists in one place only.
    @GetMapping
    public List<CustomerResponse> list(@RequestParam(required = false) Integer limit) {
        return service.listCustomers(limit);
    }
}
