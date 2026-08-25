package com.ibrhalil.forgesys.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Enables {@code @Scheduled} jobs — first consumer: {@code TokenPurgeJob} ([RISK-30]
 * stale verification-token purge). Excluded from the {@code test} profile so no
 * background job fires mid-test on the shared H2 context.
 */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
