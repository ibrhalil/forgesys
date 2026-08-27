package com.ibrhalil.forgesys.service.mail;

/** Delivery channel of the active {@link MailSender} bean — surfaces "what a send actually does" in this profile. */
public enum MailChannel {
    SMTP,
    LOG,
    IN_MEMORY
}
