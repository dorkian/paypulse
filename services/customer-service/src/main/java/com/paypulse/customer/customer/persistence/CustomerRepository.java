package com.paypulse.customer.customer.persistence;

import com.paypulse.customer.customer.domain.Customer;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Pattern: Repository. The rest of the code talks to this interface as if it were a
 * collection of Customers and never sees SQL or the database.
 *
 * <p>Spring Data writes the implementation at startup. We write no SQL and no class.
 */
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    /**
     * Newest customers first. The Pageable only carries "how many" (the limit),
     * so the service can cap it (S4.6).
     *
     * <p>Spring Data reads the method name: findAll + OrderBy + CreatedAt + Desc
     * becomes "select ... from customers order by created_at desc limit ?".
     * Returning a List (not a Page) skips the extra "count(*)" query we don't need.
     */
    List<Customer> findAllByOrderByCreatedAtDesc(Pageable pageable);
}
