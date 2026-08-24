package com.ibrhalil.forgesys.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Registers {@link SampleDataProperties} in every profile (K-47). Unlike the
 * runner-bound properties classes ({@code ModuleProperties} is registered by a
 * {@code !test} runner), {@code TenantSampleDataService} is a plain service whose bean
 * exists in the test context too, so its properties bean must exist there as well —
 * the test yaml flips the flag to {@code false}.
 */
@Configuration
@EnableConfigurationProperties(SampleDataProperties.class)
public class SampleDataConfig {
}
