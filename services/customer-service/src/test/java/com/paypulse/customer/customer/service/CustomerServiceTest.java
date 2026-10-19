package com.paypulse.customer.customer.service;

import com.paypulse.customer.customer.api.CreateCustomerRequest;
import com.paypulse.customer.customer.api.CustomerResponse;
import com.paypulse.customer.customer.domain.Customer;
import com.paypulse.customer.customer.domain.CustomerNotFoundException;
import com.paypulse.customer.customer.domain.DuplicateCustomerException;
import com.paypulse.customer.customer.persistence.CustomerRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Pageable;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit test: the service alone, with a fake repository (Mockito) and a frozen clock.
 * No Spring, no database. It checks the rules, not the wiring (B11 covers the wiring).
 */
@ExtendWith(MockitoExtension.class)
class CustomerServiceTest {

    // A frozen clock: "now" is always this exact moment, so we can assert it exactly.
    private static final Instant NOW = Instant.parse("2026-01-01T10:00:00Z");
    private static final UUID TENANT = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    CustomerRepository repository;

    CustomerService service;

    @BeforeEach
    void setUp() {
        // Built with plain `new`: possible because of constructor injection.
        service = new CustomerService(repository, Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void createNormalizesTheEmailAndStampsTheClockTime() {
        CustomerResponse response = service.createCustomer(
                new CreateCustomerRequest(TENANT, "  Mario ", " Mario@Example.COM "));

        // What was actually handed to the repository?
        ArgumentCaptor<Customer> saved = ArgumentCaptor.forClass(Customer.class);
        verify(repository).saveAndFlush(saved.capture());

        assertThat(saved.getValue().getEmail()).isEqualTo("mario@example.com");
        assertThat(saved.getValue().getName()).isEqualTo("Mario");
        assertThat(response.email()).isEqualTo("mario@example.com");
        assertThat(response.createdAt()).isEqualTo(NOW);
        assertThat(response.id()).isNotNull();
    }

    @Test
    void uniqueViolationBecomesDuplicateCustomerException() {
        when(repository.saveAndFlush(any(Customer.class)))
                .thenThrow(new DataIntegrityViolationException("uq_customers_tenant_email"));

        assertThatThrownBy(() -> service.createCustomer(
                new CreateCustomerRequest(TENANT, "Mario", "mario@example.com")))
                .isInstanceOf(DuplicateCustomerException.class)
                // The email is personal data: it must not appear in the error text.
                .hasMessageNotContaining("mario@example.com");
    }

    @Test
    void getUnknownIdThrowsNotFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getCustomer(id))
                .isInstanceOf(CustomerNotFoundException.class);
    }

    // Each row: the limit the client asked for, and the page size the service must use.
    @ParameterizedTest
    @CsvSource({"1000,100", "101,100", "100,100", "20,20", "1,1", "0,1", "-5,1"})
    void listClampsTheLimit(int requested, int expectedPageSize) {
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of());

        service.listCustomers(requested);

        assertThat(pageRequested().getPageSize()).isEqualTo(expectedPageSize);
        assertThat(pageRequested().getPageNumber()).isZero();
    }

    @Test
    void listWithoutLimitUsesTheDefault() {
        when(repository.findAllByOrderByCreatedAtDesc(any(Pageable.class))).thenReturn(List.of());

        service.listCustomers(null);

        assertThat(pageRequested().getPageSize()).isEqualTo(CustomerService.DEFAULT_LIMIT);
    }

    /** Reads back the Pageable the service passed to the repository. */
    private Pageable pageRequested() {
        ArgumentCaptor<Pageable> captor = ArgumentCaptor.forClass(Pageable.class);
        verify(repository).findAllByOrderByCreatedAtDesc(captor.capture());
        return captor.getValue();
    }
}
