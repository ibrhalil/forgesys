package com.ibrhalil.forgesys.security.jwt;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.ResponseCookie;

import java.util.Arrays;

/**
 * K-50 platform-auth surface under {@code forgesys.platform.auth.*}: separate
 * HTTP-only cookies ({@code sf_platform_*}) scoped to {@code /api/v1/platform} so
 * they never collide with the tenant cookies on the same origin (dev bare-host
 * layout), plus the platform refresh TTL and the impersonation TTL (epic F6).
 * Access-token TTL is shared with {@code jwt.access-token-ttl-minutes}.
 */
@ConfigurationProperties(prefix = "forgesys.platform.auth")
public record PlatformAuthProperties(
        Long refreshTtlDays,
        Long impersonationTtlMinutes,
        String cookiePath,
        Boolean cookieSecure,
        String cookieSameSite
) {
    public static final String ACCESS_COOKIE_NAME = "sf_platform_access_token";
    public static final String REFRESH_COOKIE_NAME = "sf_platform_refresh_token";
    public static final long DEFAULT_REFRESH_TTL_DAYS = 7;
    public static final long DEFAULT_IMPERSONATION_TTL_MINUTES = 30;
    public static final String DEFAULT_COOKIE_PATH = "/api/v1/platform";
    public static final boolean DEFAULT_COOKIE_SECURE = false;
    public static final String DEFAULT_COOKIE_SAME_SITE = "Lax";

    public long effectiveRefreshTtlDays() {
        return refreshTtlDays != null && refreshTtlDays > 0 ? refreshTtlDays : DEFAULT_REFRESH_TTL_DAYS;
    }

    public long effectiveImpersonationTtlMinutes() {
        return impersonationTtlMinutes != null && impersonationTtlMinutes > 0
                ? impersonationTtlMinutes : DEFAULT_IMPERSONATION_TTL_MINUTES;
    }

    public String effectiveCookiePath() {
        return cookiePath != null && !cookiePath.isBlank() ? cookiePath : DEFAULT_COOKIE_PATH;
    }

    public boolean effectiveCookieSecure() {
        return cookieSecure != null ? cookieSecure : DEFAULT_COOKIE_SECURE;
    }

    public String effectiveCookieSameSite() {
        return cookieSameSite != null && !cookieSameSite.isBlank() ? cookieSameSite : DEFAULT_COOKIE_SAME_SITE;
    }

    public long effectiveRefreshTtlSeconds() {
        return effectiveRefreshTtlDays() * 86_400L;
    }

    /** Path-scoped like the refresh cookie — a path-/ access cookie leaks into tenant requests and the JWT filter's platform branch then rejects them (0.2.1 regression). */
    public String buildAccessTokenCookie(String token, long expiresInSeconds) {
        return ResponseCookie.from(ACCESS_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(effectiveCookieSecure())
                .sameSite(effectiveCookieSameSite())
                .path(effectiveCookiePath())
                .maxAge(expiresInSeconds)
                .build()
                .toString();
    }

    public String buildRefreshTokenCookie(String token) {
        return ResponseCookie.from(REFRESH_COOKIE_NAME, token)
                .httpOnly(true)
                .secure(effectiveCookieSecure())
                .sameSite(effectiveCookieSameSite())
                .path(effectiveCookiePath())
                .maxAge(effectiveRefreshTtlSeconds())
                .build()
                .toString();
    }

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

    public String readRefreshCookie(HttpServletRequest request) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(c -> REFRESH_COOKIE_NAME.equals(c.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }
}
