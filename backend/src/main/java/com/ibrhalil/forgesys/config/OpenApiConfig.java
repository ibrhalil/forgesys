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
 * springdoc metadata (K-41) — served in dev/test only; prod disables springdoc via
 * yaml flags. The {@code cookieAuth} scheme documents the httpOnly cookie; the
 * Authorize button is informational (an httpOnly cookie cannot be pasted into it) —
 * call {@code /auth/login} in the same browser session to try authenticated endpoints.
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "ForgeSys API",
                version = "0.2.1",
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
