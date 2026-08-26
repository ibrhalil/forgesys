package com.ibrhalil.forgesys.service.mail;

/**
 * Mail templates at {@code mail/<key>.<lang>.html} (tr/en) — bodies live outside the
 * enum so non-developers can polish copy without a rebuild; subjects stay in code.
 * {@code templatesDir} ({@code infra/templates/}) overrides classpath per template.
 */
public enum MailTemplate {

    /** K-21 phase 1 — tenant signup verification link to the would-be admin. */
    TENANT_VERIFY("tenant-verify",
            "ForgeSys — organizasyonunuzu doğrulayın",
            "ForgeSys — verify your organization"),

    /** Tenant-internal user email verification (optional-policy flow). */
    EMAIL_VERIFY("email-verify",
            "ForgeSys — e-posta adresinizi doğrulayın",
            "ForgeSys — verify your email address"),

    /** Self-service password reset ({@code forgot-password} link). */
    PASSWORD_RESET("password-reset",
            "ForgeSys — şifre sıfırlama",
            "ForgeSys — password reset");

    private final String key;
    private final String subjectTr;
    private final String subjectEn;

    MailTemplate(String key, String subjectTr, String subjectEn) {
        this.key = key;
        this.subjectTr = subjectTr;
        this.subjectEn = subjectEn;
    }

    /** Classpath/override file key — {@code mail/<key>.<lang>.html}. */
    public String key() {
        return key;
    }

    /** Subject line for the language ("en" falls back to "tr" for anything else). */
    public String subject(String language) {
        return "en".equalsIgnoreCase(language) ? subjectEn : subjectTr;
    }
}
