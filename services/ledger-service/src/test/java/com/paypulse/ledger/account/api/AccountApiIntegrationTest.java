package com.paypulse.ledger.account.api;

import com.jayway.jsonpath.JsonPath;
import com.paypulse.common.web.CorrelationIdFilter;
import com.paypulse.ledger.PostgresTestBase;
import com.paypulse.ledger.persistence.AccountRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end test of the accounts API on real PostgreSQL (S11.2). One behaviour per test (S11.3). */
@AutoConfigureMockMvc
class AccountApiIntegrationTest extends PostgresTestBase {

    @Autowired
    MockMvc mvc;

    @Autowired
    AccountRepository repository;

    private ResultActions open(String json) throws Exception {
        return mvc.perform(post("/api/v1/accounts").contentType(MediaType.APPLICATION_JSON).content(json));
    }

    private ResultActions openValid(UUID owner) throws Exception {
        return open("""
                {"ownerId":"%s","currency":"EUR"}""".formatted(owner));
    }

    @Test
    void shouldReturn201WithLocationAndStoreTheAccountWhenTheRequestIsValid() throws Exception {
        UUID owner = UUID.randomUUID();

        String body = openValid(owner)
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/v1/accounts/")))
                .andExpect(jsonPath("$.ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.createdAt").exists())
                .andReturn().getResponse().getContentAsString();

        UUID id = UUID.fromString(JsonPath.read(body, "$.id"));
        assertThat(repository.findById(id)).isPresent();
    }

    @Test
    void shouldStartANewAccountAtZeroBalanceAndZeroCreditLimitAsScaleFourStrings() throws Exception {
        openValid(UUID.randomUUID())
                .andExpect(jsonPath("$.balance").value("0.0000"))
                .andExpect(jsonPath("$.creditLimit").value("0.0000"));
    }

    @Test
    void shouldReturnTheSameAccountWhenReadingItById() throws Exception {
        UUID owner = UUID.randomUUID();
        String created = openValid(owner).andReturn().getResponse().getContentAsString();
        String id = JsonPath.read(created, "$.id");

        mvc.perform(get("/api/v1/accounts/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.ownerId").value(owner.toString()))
                .andExpect(jsonPath("$.currency").value("EUR"))
                .andExpect(jsonPath("$.balance").value("0.0000"));
    }

    @Test
    void shouldReturn404ProblemDetailWhenTheAccountDoesNotExist() throws Exception {
        mvc.perform(get("/api/v1/accounts/" + UUID.randomUUID())
                        .header(CorrelationIdFilter.HEADER_NAME, "trace-acc-404"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:account-not-found"))
                .andExpect(jsonPath("$.correlationId").value("trace-acc-404"))
                .andExpect(content().string(not(containsString("Exception"))));
    }

    @Test
    void shouldReturn400NamingTheCurrencyFieldWhenTheCurrencyIsMissing() throws Exception {
        open("""
                {"ownerId":"%s"}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void shouldReturn400NamingTheCurrencyFieldWhenTheCurrencyIsBlank() throws Exception {
        open("""
                {"ownerId":"%s","currency":"  "}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[*].field").value(org.hamcrest.Matchers.hasItem("currency")));
    }

    @Test
    void shouldReturn400WhenTheCurrencyIsLowercase() throws Exception {
        open("""
                {"ownerId":"%s","currency":"eur"}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void shouldReturn400WhenTheCurrencyIsNotThreeLetters() throws Exception {
        open("""
                {"ownerId":"%s","currency":"EURO"}""".formatted(UUID.randomUUID()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("currency"));
    }

    @Test
    void shouldReturn400NamingTheOwnerIdFieldWhenTheOwnerIdIsMissing() throws Exception {
        open("""
                {"currency":"EUR"}""")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("ownerId"));
    }

    @Test
    void shouldReturn400ProblemDetailNot500WhenTheIdIsNotAUuid() throws Exception {
        mvc.perform(get("/api/v1/accounts/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value(startsWith("urn:paypulse:problem:")));
    }
}
