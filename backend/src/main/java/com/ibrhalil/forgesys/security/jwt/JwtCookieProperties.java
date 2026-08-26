package com.ibrhalil.forgesys.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;

import java.util.Arrays;

/**
 * Cookie attributes + refresh TTL under {@code jwt.*} (K-34): access token in body +
 * HTTP-only {@code sf_access_token} cookie; refresh in {@code sf_refresh_token} scoped
 * to {@code /api/v1/auth}. {@code Secure} defaults false in dev/test, forced true in
 * prod (RISK-24); {@code refreshTokenTtlDays} drives the Redis TTL and cookie Max-Age.
 * Also the single Set-Cookie build/expire/read path (K-40).
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

    /** Access-token Set-Cookie header — the single construction path (K-40). */
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

    /** Max-Age=0 expire header; both cookies use the access-cookie Secure flag (pre-K-40 behavior). */
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
