package com.ibrhalil.forgesys.service.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link MailProperties} (the {@code forgesys.mail.*} family). Sender beans are
 * plain {@code @Component}s discovered by package scan.
 */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {
}
