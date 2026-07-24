package com.ibrhalil.forgesys.controller;

import com.ibrhalil.forgesys.dto.CompanyRegisterRequest;
import com.ibrhalil.forgesys.dto.CompanyRegisterResponse;
import com.ibrhalil.forgesys.dto.CompanyVerifyRequest;
import com.ibrhalil.forgesys.dto.CompanyVerifyResponse;
import com.ibrhalil.forgesys.dto.LoginRequest;
import com.ibrhalil.forgesys.dto.LoginResponse;
import com.ibrhalil.forgesys.dto.MeResponse;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionRequest;
import com.ibrhalil.forgesys.dto.SubdomainSuggestionResponse;
import com.ibrhalil.forgesys.security.CustomUserDetails;
import com.ibrhalil.forgesys.security.jwt.JwtCookieProperties;
import com.ibrhalil.forgesys.service.AuthService;
import com.ibrhalil.forgesys.service.SubdomainSuggestionService;
import com.ibrhalil.forgesys.service.TenantProvisioningService;
import com.ibrhalil.forgesys.service.UserService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantProvisioningService tenantProvisioningService;
    private final AuthService authService;
    private final UserService userService;
    private final SubdomainSuggestionService subdomainSuggestionService;
    private final JwtCookieProperties cookieProperties;

    @PostMapping("/company/register")
    public ResponseEntity<CompanyRegisterResponse> registerCompany(@Valid @RequestBody CompanyRegisterRequest request) {
        // K-21 phase 1: creates a PROVISIONING Company + verification token and emails the
        // link. The tenant schema and admin user do not exist yet — the resource is not
        // ready, so 202 Accepted (not 201 Created).
        CompanyRegisterResponse body = tenantProvisioningService.createPendingCompany(request);
        return ResponseEntity.accepted().body(body);
    }

    @PostMapping("/company/verify")
    public ResponseEntity<CompanyVerifyResponse> verifyCompany(@Valid @RequestBody CompanyVerifyRequest request) {
        // K-21 phase 2: consumes the token, runs CREATE SCHEMA + Flyway + admin user,
        // promotes the Company to ACTIVE. Synchronous (heavy) — see TenantProvisioningService.
        CompanyVerifyResponse body = tenantProvisioningService.verifyAndProvision(request.token());
        return ResponseEntity.ok(body);
    }

    @PostMapping("/company/suggest-subdomain")
    public ResponseEntity<SubdomainSuggestionResponse> suggestSubdomain(
            @Valid @RequestBody SubdomainSuggestionRequest request) {
        return ResponseEntity.ok(subdomainSuggestionService.suggest(request.name()));
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request, HttpServletResponse response) {
        LoginResponse body = authService.login(request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildAccessTokenCookie(body.accessToken(), body.expiresIn()));
        return ResponseEntity.ok(body);
    }

    @GetMapping("/me")
    public ResponseEntity<MeResponse> me(@AuthenticationPrincipal CustomUserDetails user) {
        List<String> authorities = user.getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
        return ResponseEntity.ok(new MeResponse(user.getUserId(), user.getEmail(), user.getTenantSchema(), authorities));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@AuthenticationPrincipal CustomUserDetails principal,
                                       HttpServletResponse response) {
        // [RISK-21] Stamp tokenInvalidBefore so outstanding access tokens stop
        // authenticating (multi-device logout — granular revoke is Epic 2.6). The
        // cookie is also expired client-side below so the browser drops it.
        if (principal != null && principal.getUserId() != null) {
            userService.revokeTokens(principal.getUserId());
        }
        ResponseCookie expiredCookie = ResponseCookie.from(cookieProperties.effectiveCookieName(), "")
                .httpOnly(true)
                .secure(cookieProperties.effectiveCookieSecure())
                .sameSite(cookieProperties.effectiveCookieSameSite())
                .path("/")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, expiredCookie.toString());
        return ResponseEntity.noContent().build();
    }

    private String buildAccessTokenCookie(String token, long expiresInSeconds) {
        return ResponseCookie.from(cookieProperties.effectiveCookieName(), token)
                .httpOnly(true)
                .secure(cookieProperties.effectiveCookieSecure())
                .sameSite(cookieProperties.effectiveCookieSameSite())
                .path("/")
                .maxAge(expiresInSeconds)
                .build()
                .toString();
    }
}
