package com.ibrhalil.forgesys.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Cookie attributes for the {@code sf_access_token} access token. The access token is
 * delivered both in the response body (API clients) and as an HTTP-only cookie
 * (browser sessions); these properties drive the cookie half.
 *
 * <ul>
 *   <li>{@code cookieName} — cookie key (default {@code sf_access_token}).</li>
 *   <li>{@code cookieSecure} — {@code Secure} attribute. Dev/test default {@code false}
 *       (HTTP); {@code true} is forced by {@code application-prod.yaml}
 *       ([RISK-24](../../../../docs/DECISIONS.md#risk-24) — cleartext leakage over HTTP
 *       downgrade / mixed content).</li>
 *   <li>{@code cookieSameSite} — {@code SameSite} attribute (default {@code Lax}).</li>
 * </ul>
 *
 * <p>Bound under the {@code jwt.*} prefix; the {@code jwt.rsa.*} subkeys are owned by
 * {@link RsaKeyProperties} and are ignored here (record has no matching field).
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtCookieProperties(
        String cookieName,
        Boolean cookieSecure,
        String cookieSameSite
) {
    public static final String DEFAULT_COOKIE_NAME = "sf_access_token";
    public static final boolean DEFAULT_COOKIE_SECURE = false;
    public static final String DEFAULT_COOKIE_SAME_SITE = "Lax";

    /** Resolved with defaults so callers (and tests) never see nulls. */
    public String effectiveCookieName() {
        return cookieName != null && !cookieName.isBlank() ? cookieName : DEFAULT_COOKIE_NAME;
    }

    public boolean effectiveCookieSecure() {
        return cookieSecure != null ? cookieSecure : DEFAULT_COOKIE_SECURE;
    }

    public String effectiveCookieSameSite() {
        return cookieSameSite != null && !cookieSameSite.isBlank() ? cookieSameSite : DEFAULT_COOKIE_SAME_SITE;
    }
}
