package com.paypulse.common.web.error;

import com.paypulse.common.web.CorrelationIdFilter;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import java.util.UUID;
import org.springframework.web.bind.annotation.PathVariable;

class ProblemDetailExceptionHandlerTest {

    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        // "Standalone" = only our controller + our advice + our filter. No full app, fast.
        mvc = MockMvcBuilders.standaloneSetup(new TestController())
                .setControllerAdvice(new ProblemDetailExceptionHandler())
                .addFilters(new CorrelationIdFilter())
                .build();
    }

    @Test
    void notFoundMapsTo404() throws Exception {
        mvc.perform(get("/test/not-found"))
                .andExpect(status().isNotFound())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:thing-not-found"))
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void conflictMapsTo409() throws Exception {
        mvc.perform(get("/test/conflict"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:thing-conflict"));
    }

    @Test
    void businessRuleMapsTo422() throws Exception {
        mvc.perform(get("/test/rule"))
                .andExpect(status().isUnprocessableContent())
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:thing-rule"));
    }

    @Test
    void invalidBodyMapsTo400WithFieldErrors() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors[0].field").value("name"))
                .andExpect(jsonPath("$.errors[0].message").exists());
    }

    @Test
    void unexpectedErrorMapsTo500AndLeaksNothing() throws Exception {
        mvc.perform(get("/test/boom"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.detail").value("An unexpected error occurred"))
                // The secret text, the exception class name and "at com." (stack frames) must be absent.
                .andExpect(content().string(not(containsString("secret-db-password"))))
                .andExpect(content().string(not(containsString("IllegalStateException"))))
                .andExpect(content().string(not(containsString("at com."))));
    }

    @Test
    void everyErrorCarriesTheCorrelationId() throws Exception {
        mvc.perform(get("/test/not-found").header(CorrelationIdFilter.HEADER_NAME, "abc-123"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void malformedJsonMapsTo400NotFiveHundred() throws Exception {
        mvc.perform(post("/test/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header(CorrelationIdFilter.HEADER_NAME, "abc-123")
                        .content("{bad"))
                .andExpect(status().isBadRequest())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_PROBLEM_JSON))
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:bad-request"))
                .andExpect(jsonPath("$.correlationId").value("abc-123"));
    }

    @Test
    void badPathVariableMapsTo400() throws Exception {
        mvc.perform(get("/test/items/not-a-uuid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:bad-request"));
    }

    @Test
    void wrongHttpMethodMapsTo405() throws Exception {
        // /test/not-found only accepts GET
        mvc.perform(post("/test/not-found"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.type").value("urn:paypulse:problem:method-not-allowed"));
    }

    // --- test-only classes -------------------------------------------------

    @RestController
    static class TestController {

        @GetMapping("/test/not-found")
        void notFound() {
            throw new TestException("thing-not-found", ErrorCategory.NOT_FOUND);
        }

        @GetMapping("/test/conflict")
        void conflict() {
            throw new TestException("thing-conflict", ErrorCategory.CONFLICT);
        }

        @GetMapping("/test/rule")
        void rule() {
            throw new TestException("thing-rule", ErrorCategory.BUSINESS_RULE);
        }

        @PostMapping("/test/validate")
        void validate(@Valid @RequestBody Body body) {
        }

        @GetMapping("/test/boom")
        void boom() {
            throw new IllegalStateException("secret-db-password");
        }

        @GetMapping("/test/items/{id}")
        void item(@PathVariable UUID id) {
        }

    }

    record Body(@NotBlank String name) {
    }

    static class TestException extends PayPulseException {
        TestException(String code, ErrorCategory category) {
            super(code, category, "test message");
        }
    }
}
