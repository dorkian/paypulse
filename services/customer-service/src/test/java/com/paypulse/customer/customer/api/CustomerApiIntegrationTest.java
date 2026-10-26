package com.paypulse.customer.customer.api;

import com.jayway.jsonpath.JsonPath;
import com.paypulse.common.web.CorrelationIdFilter;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;


/**
 * End-to-end test of the customer API: real Spring app, real Flyway migration, real
 * PostgreSQL (a throwaway Docker container). Never H2: it behaves differently from
 * PostgreSQL, so a green H2 test would prove little (S11.2).
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class CustomerApiIntegrationTest {

    // static = one container for the whole class (fast). It is started before the
    // tests and removed after them. Same image version as infra/compose.
    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:16.15");

    // Points the app's datasource at the container. These values beat application.yml,
    // so the test needs no CUSTOMER_DB_* variables and no .env: it runs on any machine.
    @DynamicPropertySource
    static void datasource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    MockMvc mvc;

    // Each test uses its own random tenant, so tests never see each other's rows.
    private ResultActions create(UUID tenantId, String name, String email) throws Exception {
        String json = """
                {"tenantId":"%s","name":"%s","email":"%s"}""".formatted(tenantId, name, email);
        return mvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(json));
    }

    @Test
    void createReturns201WithLocation() throws Exception {
        create(UUID.randomUUID(), "Mario", "mario@example.com")
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/customers/")))
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.name").value("Mario"));
    }

    @Test
    void locationUsesTheAddressTheClientUsedWhenBehindAGateway() throws Exception {
        String json = """
                {"tenantId":"%s","name":"Gina","email":"gina@example.com"}""".formatted(UUID.randomUUID());

        // What the gateway sends: the address the CLIENT used, not the service's own.
        mvc.perform(post("/api/v1/customers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("X-Forwarded-Host", "api.example.com")
                        .header("X-Forwarded-Proto", "https")
                        .content(json))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location",
                        startsWith("https://api.example.com/api/v1/customers/")));
    }


    @Test
    void getReturnsTheSameData() throws Exception {
        UUID tenant = UUID.randomUUID();
        String created = create(tenant, "Luigi", "luigi@example.com")
                .andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/customers/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.tenantId").value(tenant.toString()))
                .andExpect(jsonPath("$.name").value("Luigi"))
                .andExpect(jsonPath("$.email").value("luigi@example.com"));
    }

    @Test
    void unknownIdReturns404ProblemJson() throws Exception {
        mvc.perform(get("/api/v1/customers/" + UUID.randomUUID())
                        .header(CorrelationIdFilter.HEADER_NAME, "trace-404"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:customer-not-found"))
                .andExpect(jsonPath("$.correlationId").value("trace-404"));
    }

    @Test
    void sameTenantAndEmailTwiceReturns409() throws Exception {
        UUID tenant = UUID.randomUUID();
        create(tenant, "Peach", "peach@example.com").andExpect(status().isCreated());

        create(tenant, "Peach Again", "peach@example.com")
                .andExpect(status().isConflict())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:customer-email-taken"));
    }

    @Test
    void sameEmailWithDifferentCaseAlsoReturns409() throws Exception {
        UUID tenant = UUID.randomUUID();
        create(tenant, "Toad", "toad@example.com").andExpect(status().isCreated());

        // Only works because Customer.create lowercases the email before saving.
        create(tenant, "Toad Again", "TOAD@Example.COM")
                .andExpect(status().isConflict());
    }

    @Test
    void sameEmailInAnotherTenantIsAllowed() throws Exception {
        create(UUID.randomUUID(), "Bowser", "bowser@example.com").andExpect(status().isCreated());
        create(UUID.randomUUID(), "Bowser", "bowser@example.com").andExpect(status().isCreated());
    }

    @Test
    void invalidEmailReturns400WithFieldError() throws Exception {
        create(UUID.randomUUID(), "Yoshi", "not-an-email")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("email"));
    }

    @Test
    void everyResponseCarriesACorrelationId() throws Exception {
        // success path
        create(UUID.randomUUID(), "Daisy", "daisy@example.com")
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
        // error path
        mvc.perform(get("/api/v1/customers/" + UUID.randomUUID()))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME));
    }
}
