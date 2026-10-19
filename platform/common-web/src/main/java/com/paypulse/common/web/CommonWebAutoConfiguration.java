package com.paypulse.common.web;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

import com.paypulse.common.web.error.ProblemDetailExceptionHandler;
import static org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication.Type.SERVLET;

/**
 * Auto-configuration: any service that has common-web on its classpath
 * gets these beans without writing any code.
 */
@AutoConfiguration
public class CommonWebAutoConfiguration {
    @Bean
    public CorrelationIdFilter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
    
    // Only for servlet (Spring MVC) apps. The api-gateway is reactive (WebFlux),
    // and this handler uses MVC types, so there it must stay switched off.
    @Bean
    @ConditionalOnWebApplication(type = SERVLET)
    public ProblemDetailExceptionHandler problemDetailExceptionHandler() {
        return new ProblemDetailExceptionHandler();
    }
}
