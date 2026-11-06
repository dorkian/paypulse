package com.paypulse.ledger;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Base class for every ledger test that needs the database: the real application, the real Flyway
 * migration and a real PostgreSQL in a throwaway container (same version as infra/compose).
 * Subclasses share one container and one Spring context, so the suite stays fast.
 */
@SpringBootTest
@Testcontainers
public abstract class PostgresTestBase {

    @Container
    protected static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15");

    // These values beat application.yml, so the tests need no LEDGER_DB_* variables and no .env.
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }
}
