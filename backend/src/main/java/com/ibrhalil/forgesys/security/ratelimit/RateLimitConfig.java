package com.ibrhalil.forgesys.security.ratelimit;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires the {@link RateLimitProperties} bound under {@code forgesys.security.rate-limit}
 * (Faz 3). The {@link RateLimiter} + {@link RateLimitFilter} beans are component-scanned;
 * this only enables the properties record.
 */
@Configuration
@EnableConfigurationProperties(RateLimitProperties.class)
public class RateLimitConfig {
}
