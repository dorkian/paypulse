package com.paypulse.ledger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Makes "what time is it?" injectable, so business code never calls Instant.now() and a test
 * can freeze time with Clock.fixed(...).
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        // UTC on purpose: we store TIMESTAMPTZ and compare moments, never local times.
        return Clock.systemUTC();
    }
}
