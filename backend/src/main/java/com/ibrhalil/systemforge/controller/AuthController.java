package com.ibrhalil.systemforge.controller;

import com.ibrhalil.systemforge.dto.CompanyRegisterRequest;
import com.ibrhalil.systemforge.dto.LoginRequest;
import com.ibrhalil.systemforge.dto.LoginResponse;
import com.ibrhalil.systemforge.dto.MeResponse;
import com.ibrhalil.systemforge.entity.Company;
import com.ibrhalil.systemforge.security.CustomUserDetails;
import com.ibrhalil.systemforge.service.AuthService;
import com.ibrhalil.systemforge.service.TenantProvisioningService;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
public class AuthController {

    private final TenantProvisioningService tenantProvisioningService;
    private final AuthService authService;

    @Value("${jwt.cookie-name:sf_access_token}")
    private String cookieName;

    @Value("${jwt.cookie-secure:false}")
    private boolean cookieSecure;

    @Value("${jwt.cookie-same-site:Lax}")
    private String cookieSameSite;

    @PostMapping("/company/register")
    public ResponseEntity<Map<String, Object>> registerCompany(@Valid @RequestBody CompanyRegisterRequest request) {
        Company company = tenantProvisioningService.provisionTenant(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(Map.of(
                "id", company.getId(),
                "name", company.getName(),
                "subdomain", company.getSubdomain(),
                "schemaName", company.getSchemaName()
        ));
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

    private String buildAccessTokenCookie(String token, long expiresInSeconds) {
        return ResponseCookie.from(cookieName, token)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite(cookieSameSite)
                .path("/")
                .maxAge(expiresInSeconds)
                .build()
                .toString();
    }
}
