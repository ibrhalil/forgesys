package com.ibrhalil.forgesys.dto;

import java.util.List;

/**
 * K-51 mail-test info: the active delivery channel (profile-split {@code MailSender}),
 * effective config and the template catalog — what a platform admin needs before
 * sending a test mail.
 *
 * @param channel        SMTP (real delivery), LOG (dev — only logged) or IN_MEMORY (test)
 * @param from           effective RFC 822 From header
 * @param defaultLanguage configured {@code forgesys.mail.default-language}
 * @param templatesDir   configured override dir (empty = jar classpath copy, K-51)
 * @param templates      renderable {@link com.ibrhalil.forgesys.service.mail.MailTemplate} catalog
 */
public record PlatformMailInfoResponse(
        String channel,
        String from,
        String defaultLanguage,
        String templatesDir,
        List<PlatformMailTemplateInfo> templates) {
}
