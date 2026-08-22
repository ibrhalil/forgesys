package com.ibrhalil.forgesys.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;

import java.util.Arrays;

/**
 * Cookie attributes + refresh-token TTL for JWT auth. The access token is delivered
 * both in the response body (API clients) and as an HTTP-only cookie
 * ({@code sf_access_token}, browser sessions); the refresh token likewise uses its own
 * {@code sf_refresh_token} cookie scoped to {@code /api/v1/auth} (K-34). These
 * properties drive the cookie halves.
 *
 * <ul>
 *   <li>{@code cookieName} / {@code refreshCookieName} — cookie keys.</li>
 *   <li>{@code cookieSecure} / {@code refreshCookieSecure} — {@code Secure} attribute.
 *       Dev/test default {@code false} (HTTP); {@code true} is forced by
 *       {@code application-prod.yaml} ([RISK-24](../../../../docs/DECISIONS.md#risk-24)
 *       — cleartext leakage over HTTP downgrade / mixed content).</li>
 *   <li>{@code cookieSameSite} — {@code SameSite} attribute (default {@code Lax}).</li>
 *   <li>{@code refreshTokenTtlDays} — refresh-token lifetime (default 7). Drives the
 *       Redis TTL and the refresh-cookie {@code Max-Age}.</li>
 *   <li>{@code refreshCookiePath} — refresh-cookie {@code Path} (default
 *       {@code /api/v1/auth} so the cookie is sent only to auth endpoints).</li>
 * </ul>
 *
 * <p>Bound under the {@code jwt.*} prefix; the {@code jwt.rsa.*} subkeys are owned by
 * {@link RsaKeyProperties} and are ignored here (record has no matching field).
 * Beyond the raw attributes, this record also carries the {@code Set-Cookie}
 * build/expire/read helpers (K-40) — the single cookie construction path shared by
 * the auth and session controllers.
 */
@ConfigurationProperties(prefix = "jwt")
public record JwtCookieProperties(
        String cookieName,
        Boolean cookieSecure,
        String cookieSameSite,
        Long refreshTokenTtlDays,
        String refreshCookieName,
        Boolean refreshCookieSecure,
        String refreshCookiePath
) {
    public static final String DEFAULT_COOKIE_NAME = "sf_access_token";
    public static final boolean DEFAULT_COOKIE_SECURE = false;
    public static final String DEFAULT_COOKIE_SAME_SITE = "Lax";
    public static final long DEFAULT_REFRESH_TOKEN_TTL_DAYS = 7;
    public static final String DEFAULT_REFRESH_COOKIE_NAME = "sf_refresh_token";
    public static final boolean DEFAULT_REFRESH_COOKIE_SECURE = false;
    public static final String DEFAULT_REFRESH_COOKIE_PATH = "/api/v1/auth";

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

    public long effectiveRefreshTokenTtlDays() {
        return refreshTokenTtlDays != null && refreshTokenTtlDays > 0 ? refreshTokenTtlDays : DEFAULT_REFRESH_TOKEN_TTL_DAYS;
    }

    public String effectiveRefreshCookieName() {
        return refreshCookieName != null && !refreshCookieName.isBlank() ? refreshCookieName : DEFAULT_REFRESH_COOKIE_NAME;
    }

    public boolean effectiveRefreshCookieSecure() {
        return refreshCookieSecure != null ? refreshCookieSecure : DEFAULT_REFRESH_COOKIE_SECURE;
    }

    public String effectiveRefreshCookiePath() {
        return refreshCookiePath != null && !refreshCookiePath.isBlank() ? refreshCookiePath : DEFAULT_REFRESH_COOKIE_PATH;
    }

    /** Refresh-token lifetime in seconds (Redis TTL). */
    public long effectiveRefreshTokenTtlSeconds() {
        return effectiveRefreshTokenTtlDays() * 86_400L;
    }

    /**
     * Builds the {@code Set-Cookie} header for the access-token cookie. Single
     * source for cookie construction (K-40) — controllers never assemble
     * {@code ResponseCookie}s themselves.
     */
    public String buildAccessTokenCookie(String token, long expiresInSeconds) {
        return ResponseCookie.from(effectiveCookieName(), token)
                .httpOnly(true)
                .secure(effectiveCookieSecure())
                .sameSite(effectiveCookieSameSite())
                .path("/")
                .maxAge(expiresInSeconds)
                .build()
                .toString();
    }

    /** Builds the {@code Set-Cookie} header for the refresh-token cookie. */
    public String buildRefreshTokenCookie(String token) {
        return ResponseCookie.from(effectiveRefreshCookieName(), token)
                .httpOnly(true)
                .secure(effectiveRefreshCookieSecure())
                .sameSite(effectiveCookieSameSite())
                .path(effectiveRefreshCookiePath())
                .maxAge(effectiveRefreshTokenTtlSeconds())
                .build()
                .toString();
    }

    /**
     * Builds a {@code Max-Age=0} expire header for the given cookie name.
     * Both cookies expire with the access-cookie {@code Secure} flag — preserved
     * pre-K-40 behavior (the access flag is the strictest default).
     */
    public String expireCookie(String name, String path) {
        return ResponseCookie.from(name, "")
                .httpOnly(true)
                .secure(effectiveCookieSecure())
                .sameSite(effectiveCookieSameSite())
                .path(path)
                .maxAge(0)
                .build()
                .toString();
    }

    /** Reads the refresh-token cookie from the request; {@code null} when absent. */
    public String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> effectiveRefreshCookieName().equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
