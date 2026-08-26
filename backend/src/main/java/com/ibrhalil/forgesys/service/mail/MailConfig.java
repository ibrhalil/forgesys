package com.ibrhalil.forgesys.service.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Wires {@link MailProperties} ({@code forgesys.mail.*}); senders are scanned {@code @Component}s. */
@Configuration
@EnableConfigurationProperties(MailProperties.class)
public class MailConfig {
}
