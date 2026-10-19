package com.paypulse.customer.customer.service;

import com.paypulse.customer.customer.api.CreateCustomerRequest;
import com.paypulse.customer.customer.api.CustomerResponse;
import com.paypulse.customer.customer.domain.Customer;
import com.paypulse.customer.customer.domain.CustomerNotFoundException;
import com.paypulse.customer.customer.domain.DuplicateCustomerException;
import com.paypulse.customer.customer.persistence.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

/**
 * The business logic of the customer feature. Controllers call this, this calls the
 * repository. It takes and returns DTOs only, never entities, so the web layer never
 * touches the database model (S1.2, S1.3).
 */
@Service
public class CustomerService {

    private static final Logger log = LoggerFactory.getLogger(CustomerService.class);

    static final int DEFAULT_LIMIT = 50;
    static final int MAX_LIMIT = 100;

    private final CustomerRepository customers;
    private final Clock clock;

    // Constructor injection: both fields can be final, nothing is hidden, and a unit
    // test can build the service with plain `new` (a fake repository and a fixed Clock).
    public CustomerService(CustomerRepository customers, Clock clock) {
        this.customers = customers;
        this.clock = clock;
    }

    /**
     * Creates a customer. One transaction: either the row is saved or nothing is.
     */
    @Transactional
    public CustomerResponse createCustomer(CreateCustomerRequest request) {
        Customer customer = Customer.create(
                request.tenantId(), request.name(), request.email(), clock.instant());
        try {
            // saveAndFlush sends the INSERT now, inside this try. A plain save() may
            // delay the INSERT until the transaction commits, which is AFTER this method,
            // and the unique-constraint error would escape our catch.
            customers.saveAndFlush(customer);
        } catch (DataIntegrityViolationException e) {
            // The database is the referee: the UNIQUE (tenant_id, email) constraint
            // wins even when two requests race. We turn its error into a domain one.
            throw new DuplicateCustomerException();
        }
        // The id only: the email is personal data and must stay out of logs (S8.5).
        log.info("Customer created id={}", customer.getId());
        return CustomerResponse.from(customer);
    }

    // readOnly = true: tells Hibernate not to track changes (faster) and some databases
    // to optimize. It also documents "this method never writes".
    @Transactional(readOnly = true)
    public CustomerResponse getCustomer(UUID id) {
        return customers.findById(id)
                .map(CustomerResponse::from)
                .orElseThrow(() -> new CustomerNotFoundException(id));
    }

    /**
     * Newest first. The caller may ask for fewer or more rows, but never more than
     * MAX_LIMIT: an API without a cap lets one request read the whole table (S4.6).
     * Note: until L2 (tenant from the login token) this lists customers of all tenants.
     */
    @Transactional(readOnly = true)
    public List<CustomerResponse> listCustomers(Integer limit) {
        int size = (limit == null) ? DEFAULT_LIMIT : Math.min(Math.max(limit, 1), MAX_LIMIT);
        return customers.findAllByOrderByCreatedAtDesc(PageRequest.of(0, size)).stream()
                .map(CustomerResponse::from)
                .toList();
    }
}
