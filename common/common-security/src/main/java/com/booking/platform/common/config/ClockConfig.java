package com.booking.platform.common.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/**
 * Provides a UTC {@link Clock} bean for all services that include common-security.
 *
 * <p>Using {@code @ConditionalOnMissingBean} so individual services can override
 * with their own Clock (e.g. a fixed clock in tests) without conflict.
 */
@Configuration("commonSecurityClockConfig")
public class ClockConfig {

    @Bean
    @ConditionalOnMissingBean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
