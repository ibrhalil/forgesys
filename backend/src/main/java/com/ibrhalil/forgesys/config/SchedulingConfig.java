package com.ibrhalil.forgesys.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables {@code @Scheduled} jobs; excluded from test so no job fires mid-test on the shared H2 context. */
@Configuration
@EnableScheduling
@Profile("!test")
public class SchedulingConfig {
}
