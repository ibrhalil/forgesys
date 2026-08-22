package com.ibrhalil.forgesys.config;

import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI metadata (K-41). springdoc generates the spec from the controllers at
 * runtime ({@code /v3/api-docs}, Swagger UI at {@code /swagger-ui.html}) — enabled
 * in dev/test, disabled in prod via {@code springdoc.*} flags in
 * {@code application-prod.yaml}.
 *
 * <p><strong>Auth model documented, not operated:</strong> the API authenticates
 * with an httpOnly JWT cookie ({@code sf_access_token}) set by
 * {@code POST /api/v1/auth/login}; the browser attaches it automatically and JS
 * never reads it. The {@code cookieAuth} security scheme documents this — the
 * Swagger UI "Authorize" button is informational (an httpOnly cookie cannot be
 * pasted into it). To try authenticated endpoints from Swagger UI, call
 * {@code /auth/login} first in the same browser session (cookies flow same-origin)
 * — or use a tenant subdomain host plus the dev-only {@code X-Tenant-ID} header
 * when hitting {@code localhost} directly.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ForgeSys API",
                version = "0.1.1",
                description = "Multi-tenant SaaS platform API. Auth: httpOnly JWT cookies "
                        + "(sf_access_token / sf_refresh_token) issued by POST /api/v1/auth/login. "
                        + "Tenant is resolved per subdomain; in dev an X-Tenant-ID header fallback is active."),
        security = @SecurityRequirement(name = "cookieAuth"))
@SecurityScheme(
        name = "cookieAuth",
        type = SecuritySchemeType.APIKEY,
        in = SecuritySchemeIn.COOKIE,
        paramName = JwtCookieProperties.DEFAULT_COOKIE_NAME,
        description = "httpOnly access-token cookie set by the login endpoint; sent automatically "
                + "by the browser (same-origin). The Authorize button is documentation only.")
public class OpenApiConfig {
}
