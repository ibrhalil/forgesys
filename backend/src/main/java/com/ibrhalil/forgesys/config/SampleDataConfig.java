package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link SampleDataProperties} in EVERY profile (K-47) — the seed service
 * bean exists in the test context too, so its properties must exist there as well.
 */
@Configuration
@EnableConfigurationProperties(SampleDataProperties.class)
public class SampleDataConfig {
}
