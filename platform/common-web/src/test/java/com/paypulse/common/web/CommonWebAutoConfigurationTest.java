package com.paypulse.common.web;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.WebApplicationContextRunner;

import com.paypulse.common.web.error.ProblemDetailExceptionHandler;

import static org.assertj.core.api.Assertions.assertThat;

class CommonWebAutoConfigurationTest {

    // Starts a tiny Spring context in milliseconds, no full app needed.
    private final WebApplicationContextRunner runner = new WebApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(CommonWebAutoConfiguration.class));

    @Test
    void registersCorrelationIdFilter() {
        runner.run(context -> assertThat(context).hasSingleBean(CorrelationIdFilter.class));
    }
    
    @Test
    void registersErrorHandlerInServletApp() {
        // WebApplicationContextRunner simulates a servlet app, so the condition passes.
        runner.run(context -> assertThat(context)
                .hasSingleBean(ProblemDetailExceptionHandler.class));
    }

}
