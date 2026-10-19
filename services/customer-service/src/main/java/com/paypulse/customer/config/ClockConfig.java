package com.paypulse.customer.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Makes "what time is it?" something we can inject, instead of calling Instant.now()
 * inside business code (S3.4).
 *
 * <p>In production the service gets the real UTC clock. In tests we replace this bean
 * (or build the service by hand) with Clock.fixed(...), so a test can say
 * "it is exactly 2026-01-01T10:00:00Z" and check created_at to the nanosecond.
 */
@Configuration
public class ClockConfig {

    @Bean
    public Clock clock() {
        // UTC on purpose: we store TIMESTAMPTZ and compare moments, never local times.
        return Clock.systemUTC();
    }
}
