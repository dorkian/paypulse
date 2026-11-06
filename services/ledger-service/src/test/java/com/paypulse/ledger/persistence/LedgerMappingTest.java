package com.paypulse.ledger.persistence;

import com.paypulse.ledger.PostgresTestBase;
import jakarta.persistence.EntityManager;
import jakarta.persistence.metamodel.EntityType;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Checks the JPA side. The application starts with ddl-auto=validate, so if an entity and the Flyway
 * schema disagree (wrong column, wrong type) the Spring context fails to start and EVERY test in this
 * module fails. These tests add: the three entities exist, and Account is ready for optimistic locking.
 */
class LedgerMappingTest extends PostgresTestBase {

    @Autowired
    EntityManager em;

    @Test
    void theThreeLedgerEntitiesAreMapped() {
        Set<String> names = em.getMetamodel().getEntities().stream()
                .map(EntityType::getName).collect(Collectors.toSet());
        assertThat(names).containsExactlyInAnyOrder("Account", "LedgerTransaction", "LedgerEntry");
    }

    @Test
    void accountHasAVersionAttributeForOptimisticLocking() {
        // @Version is what card L1-3 uses to detect a lost update.
        EntityType<?> account = em.getMetamodel().getEntities().stream()
                .filter(e -> e.getName().equals("Account")).findFirst().orElseThrow();
        assertThat(account.hasVersionAttribute()).isTrue();
    }
}
